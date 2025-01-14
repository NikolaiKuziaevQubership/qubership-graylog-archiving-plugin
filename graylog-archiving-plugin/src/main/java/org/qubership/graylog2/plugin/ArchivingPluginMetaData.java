package org.qubership.graylog2.plugin;

import org.graylog2.plugin.PluginMetaData;
import org.graylog2.plugin.ServerStatus.Capability;
import org.graylog2.plugin.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.net.URI;
import java.net.URL;
import java.util.Collections;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;

public class ArchivingPluginMetaData implements PluginMetaData {

    private static final Logger LOG = LoggerFactory.getLogger(ArchivingPluginMetaData.class);

    private static final String PLUGIN_PROPERTIES = "org.qubership.graylog-archiving-plugin/graylog-plugin.properties";

    private static final Version UNKNOWN_VERSION = from(0, 0, 0, "unknown");

    @Override
    public String getUniqueId() {
        return ArchivingPlugin.class.getName();
    }

    @Override
    public String getName() {
        return "ArchivingPlugin";
    }

    @Override
    public String getAuthor() {
        return "Qubership";
    }

    @Override
    public URI getURL() {
        return URI.create("https://github.com/Netcracker/qubership-graylog-archiving-plugin");
    }

    @Override
    public Version getVersion() {
        return fromPluginProperties(getClass(), PLUGIN_PROPERTIES, "version", UNKNOWN_VERSION);
    }

    @Override
    public String getDescription() {
        return "Plugin for archiving messages";
    }

    @Override
    public Version getRequiredVersion() {
        return fromPluginProperties(getClass(), PLUGIN_PROPERTIES, "graylog.version", UNKNOWN_VERSION);
    }

    @Override
    public Set<Capability> getRequiredCapabilities() {
        return Collections.emptySet();
    }

    private static Version from(int major, int minor, int patch, String preRelease) {
        return from(major, minor, patch, preRelease, (String)null);
    }

    private static Version from(int major, int minor, int patch, String preRelease, String buildMetadata) {
        return new Version(major, minor, patch, preRelease, buildMetadata);
    }

    private static Version fromPluginProperties(Class<?> pluginClass, String path, String propertyName, Version defaultVersion) {
        return fromClasspathProperties(pluginClass, path, propertyName, (String)null, (String)null, defaultVersion);
    }

    private static Version fromClasspathProperties(@Nonnull Class<?> clazz, String path, String propertyName, String gitPath, String gitPropertyName, Version defaultVersion) {
        try {
            URL resource = getResource(clazz, path);
            Properties versionProperties = new Properties();
            versionProperties.load(resource.openStream());
            com.github.zafarkhaja.semver.Version version = com.github.zafarkhaja.semver.Version.valueOf(versionProperties.getProperty(propertyName));
            int major = version.getMajorVersion();
            int minor = version.getMinorVersion();
            int patch = version.getPatchVersion();
            String qualifier = version.getPreReleaseVersion();
            String commitSha = null;

            try {
                Properties git = new Properties();
                URL gitResource = getResource(clazz, gitPath);
                git.load(gitResource.openStream());
                commitSha = git.getProperty(gitPropertyName);
                if (commitSha != null && commitSha.length() > 7) {
                    commitSha = commitSha.substring(0, 7);
                }
            } catch (Exception var16) {
                LOG.debug("Git commit details are not available, skipping.", var16);
            }

            return from(major, minor, patch, qualifier, commitSha);
        } catch (Exception var17) {
            LOG.error("Unable to read " + path + ", this build has no version number.", var17);
            return defaultVersion;
        }
    }

    private static URL getResource(Class<?> clazz, String path) {
        URL url = ((Class) Objects.requireNonNull(clazz, "Class argument is null!")).getClassLoader().getResource(path);
        return (URL)Objects.requireNonNull(url, "Resource <" + path + "> not found.");
    }
}