package net.runelite.client.plugins.tscripts.runtime;

import lombok.Getter;
import net.runelite.client.plugins.tscripts.ui.debug.VariableInspector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VariableMap
{
    @Getter
    private final Map<String, Object> variableMap = new HashMap<>();
    private final List<String> frozenVariables = new ArrayList<>();

    public void put(String key, Object value)
    {
        if (!isFrozen(key))
            variableMap.put(key, value);
        pollVariableInspector();
    }

    public Object get(String key)
    {
        return variableMap.getOrDefault(key, "");
    }

    public boolean containsKey(String key)
    {
        return variableMap.containsKey(key);
    }

    public void clear()
    {
        variableMap.clear();
        frozenVariables.clear();
        pollVariableInspector();
    }

    private void pollVariableInspector()
    {
        VariableInspector.update(variableMap);
    }

    public boolean isFrozen(String key)
    {
        return frozenVariables.contains(key);
    }

    public void toggleFreeze(String key)
    {
        if (isFrozen(key))
            unfreeze(key);
        else
            freeze(key);
    }

    public void freeze(String key)
    {
        frozenVariables.add(key);
    }

    public void unfreeze(String key)
    {
        frozenVariables.remove(key);
    }
}
