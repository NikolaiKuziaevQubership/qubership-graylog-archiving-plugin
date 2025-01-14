package org.qubership.graylog2.plugin;

import org.graylog2.plugin.Plugin;
import org.graylog2.plugin.PluginMetaData;
import org.graylog2.plugin.PluginModule;

import java.util.Collection;
import java.util.Collections;

public class ArchivingPlugin implements Plugin {

    @Override
    public PluginMetaData metadata() {
        return new ArchivingPluginMetaData();
    }

    @Override
    public Collection<PluginModule> modules() {
        return Collections.singletonList(new ArchivingModule());
    }
}