package org.lilyproject.clientmetrics;

import java.util.ArrayList;
import java.util.List;

public class ListMetricsPlugin implements MetricsPlugin {
    private List<MetricsPlugin> plugins = new ArrayList<MetricsPlugin>();

    public void add(MetricsPlugin plugin) {
        this.plugins.add(plugin);
    }

    public void beforeReport(Metrics metrics) {
        for (MetricsPlugin plugin : plugins) {
            plugin.beforeReport(metrics);
        }
    }

    public void afterIncrement(Metrics metrics) {
        for (MetricsPlugin plugin : plugins) {
            plugin.afterIncrement(metrics);
        }
    }

    public List<String> getExtraInfoLines() {
        List<String> result = new ArrayList<String>();
        for (MetricsPlugin plugin : plugins) {
            result.addAll(plugin.getExtraInfoLines());
        }
        return result;
    }
}