package at.porscheinformatik.sonarqube.licensecheck.gradle;

import at.porscheinformatik.sonarqube.licensecheck.Dependency;
import at.porscheinformatik.sonarqube.licensecheck.interfaces.Scanner;
import at.porscheinformatik.sonarqube.licensecheck.mavenlicense.MavenLicenseService;

import org.codehaus.plexus.util.StringUtils;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReader;

import java.io.*;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class GradleDependencyScanner implements Scanner
{
    private static final Logger LOGGER = Loggers.get(GradleDependencyScanner.class);
    private final MavenLicenseService mavenLicenseService;

    public GradleDependencyScanner(MavenLicenseService mavenLicenseService)
    {
        this.mavenLicenseService = mavenLicenseService;
    }

    @Override
    public Set<Dependency> scan(File moduleDir)
    {
        Map<Pattern, String> defaultLicenseMap = mavenLicenseService.getLicenseMap();

        File licenseDetailsJsonFile = new File(moduleDir, "build" + File.separator + "reports" + File.separator
            + "dependency-license" + File.separator + "license-details.json");

        if (!licenseDetailsJsonFile.exists())
        {
            LOGGER.info("No license-details.json file found in {} - skipping Gradle dependency scan",
                licenseDetailsJsonFile.getPath());
            return Collections.emptySet();
        }

        Map<String, Set<Dependency>> dependenciesMap = readLicenseDetailsJson(licenseDetailsJsonFile)
            .stream()
            .map(d -> mapMavenDependencyToLicense(defaultLicenseMap, d))
            .collect(Collectors.groupingBy(Dependency::getName, Collectors.toSet()));

        Set<Dependency> deps = new TreeSet<>(Comparator.comparing(Dependency::getName));
        dependenciesMap.forEach((key, value) ->
        {
            if (value.size() == 1) {
                deps.addAll(value);
            } else {
                String licenseName = value.stream().map(Dependency::getLicense).collect(Collectors.joining(" AND "));
                Dependency d = value.iterator().next();
                d.setLicense("(" + licenseName + ")");
                deps.add(d);
            }
        });
        return deps;
    }

    private Set<Dependency> readLicenseDetailsJson(File licenseDetailsJsonFile)
    {
        Set<Dependency> dependencySet = new HashSet<>();
        try (InputStream fis = new FileInputStream(licenseDetailsJsonFile);
            JsonReader jsonReader = Json.createReader(fis))
        {
            JsonArray arr = jsonReader.readObject().getJsonArray("dependencies");
            prepareDependencySet(dependencySet, arr);
            return dependencySet;
        }
        catch (IOException e)
        {
            LOGGER.error("Problems reading Gradle license file {}: {}",
                licenseDetailsJsonFile.getPath(), e.getMessage());
        }
        return dependencySet;
    }

    private void prepareDependencySet(Set<Dependency> dependencySet, JsonArray arr)
    {
        for (javax.json.JsonValue entry : arr)
        {
            JsonObject jsonDepObj = entry.asJsonObject();
            JsonArray arrModuleUrls = jsonDepObj.getJsonArray("moduleUrls");
            Set<String> moduleLicenseSet = getModuleLicenseSetFromJsonObject(jsonDepObj);

            final String moduleLicenseUrl = arrModuleUrls != null ? arrModuleUrls.getString(0, null) : null;

            Set<Dependency> moduleLicenseDependencySet = moduleLicenseSet.stream().map( ml -> {
                Dependency dep = new Dependency(jsonDepObj.getString("moduleName", null),
                    jsonDepObj.getString("moduleVersion", null), ml, ml);
                dep.setPomPath(moduleLicenseUrl);
                return dep;
            }).collect(Collectors.toSet());
            dependencySet.addAll(moduleLicenseDependencySet);
        }
    }

    private Set<String> getModuleLicenseSetFromJsonObject(JsonObject jsonDepObj) {
        JsonArray arrModuleLicenses = jsonDepObj.getJsonArray("moduleLicenses");
        if (arrModuleLicenses == null) {
            return Collections.singleton(null);
        }
        return arrModuleLicenses.getValuesAs(JsonObject.class).stream().map(license ->
            {
                String moduleLicense = license.getString("moduleLicense", null);
                if (moduleLicense == null) {
                    moduleLicense = license.getString("moduleLicenseUrl", null);
                    if (moduleLicense != null) {
                        moduleLicense = moduleLicense.substring(0, moduleLicense.indexOf(","));
                    }
                }
                return moduleLicense;
            }
        ).collect(Collectors.toSet());
    }

    private Dependency mapMavenDependencyToLicense(Map<Pattern, String> defaultLicenseMap, Dependency dependency)
    {
        if (StringUtils.isBlank(dependency.getLicense()))
        {
            LOGGER.error(" License not found for Dependency {}", dependency);
            return dependency;
        }

        for (Map.Entry<Pattern, String> allowedDependency : defaultLicenseMap.entrySet())
        {
            if (allowedDependency.getKey().matcher(dependency.getLicense()).matches())
            {
                dependency.setLicense(allowedDependency.getValue());
                break;
            }
        }
        return dependency;
    }
}
