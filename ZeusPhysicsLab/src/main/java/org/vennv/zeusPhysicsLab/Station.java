package org.vennv.zeusPhysicsLab;

import java.util.List;

public record Station(
    int number,
    String id,
    String description,
    StationCategory category,
    List<String> featureGroups,
    List<String> targetFeatures,
    List<String> instructions,
    List<String> expectedEvents,
    int durationSeconds
) {
    public String manifestJson() {
        return "{"
            + "\"number\":" + number + ","
            + "\"station_id\":" + quote(id) + ","
            + "\"category\":" + quote(category.name()) + ","
            + "\"feature_groups\":" + jsonArray(featureGroups) + ","
            + "\"target_features\":" + jsonArray(targetFeatures) + ","
            + "\"label\":\"legit_or_edge_legit\"," 
            + "\"description\":" + quote(description) + ","
            + "\"route_instructions\":" + jsonArray(instructions) + ","
            + "\"duration_seconds\":" + durationSeconds + ","
            + "\"expected_packets_events\":" + jsonArray(expectedEvents)
            + "}";
    }

    private static String jsonArray(List<String> values) {
        return values.stream().map(Station::quote).toList().toString();
    }

    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
