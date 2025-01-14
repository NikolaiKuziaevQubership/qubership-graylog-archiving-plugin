package org.qubership.graylog2.plugin.utils;

import org.json.JSONObject;

import javax.inject.Singleton;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Singleton
public class ParametersProcessor {

    public List<String> getList(JSONObject jsonObject, String key) {
        if (jsonObject.has(key)) {
             return jsonObject
                    .getJSONArray(key)
                    .toList()
                    .stream()
                    .map(Object::toString)
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    public String getString(JSONObject jsonObject, String key) {
        if (jsonObject.has(key))
            return jsonObject.get(key).toString();
        return null;
    }
}
