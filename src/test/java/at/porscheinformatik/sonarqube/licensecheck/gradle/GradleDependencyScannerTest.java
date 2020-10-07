package at.porscheinformatik.sonarqube.licensecheck.gradle;

import at.porscheinformatik.sonarqube.licensecheck.Dependency;
import at.porscheinformatik.sonarqube.licensecheck.mavenlicense.MavenLicenseService;

import org.junit.Test;
import org.mockito.Mockito;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

public class GradleDependencyScannerTest
{
    private MavenLicenseService mockLicenseService()
    {
        Map<Pattern, String> licenseMap = new HashMap<>();
        licenseMap.put(Pattern.compile(".*Apache.*2.*"), "Apache-2.0");
        licenseMap.put(Pattern.compile(".*MIT.*"), "MIT");
        licenseMap.put(Pattern.compile(".*GNU.*3\\.0.*"), "AGPL-3.0");

        MavenLicenseService licenseService = Mockito.mock(MavenLicenseService.class);
        when(licenseService.getLicenseMap()).thenReturn(licenseMap);
        return licenseService;
    }

    @Test
    public void testScannerWithMissingJsonFile()
    {
        GradleDependencyScanner scanner = new GradleDependencyScanner(mockLicenseService());
        Set<Dependency> dependencies = scanner.scan(new File("/abc"));
        assertEquals(0, dependencies.size());
    }

    @Test
    public void testScanner()
    {
        GradleDependencyScanner scanner = new GradleDependencyScanner(mockLicenseService());
        Path resourceDirectory = Paths.get("src", "test", "resources");
        String absolutePath = resourceDirectory.toFile().getAbsolutePath();

        Set<Dependency> dependencies = scanner.scan(new File(absolutePath));
        assertEquals(43, dependencies.size());
    }

    @Test
    public void testScannerForMinJson()
    {
        GradleDependencyScanner scanner = new GradleDependencyScanner(mockLicenseService());
        Path resourceDirectory = Paths.get("src", "test", "resources","min");
        String absolutePath = resourceDirectory.toFile().getAbsolutePath();

        Set<Dependency> dependencies = scanner.scan(new File(absolutePath));
        assertEquals(1, dependencies.size());
    }
}
