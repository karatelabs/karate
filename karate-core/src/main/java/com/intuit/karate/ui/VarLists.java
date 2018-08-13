package com.intuit.karate.ui;

import com.intuit.karate.ScriptValue;
import com.intuit.karate.ScriptValueMap;
import com.intuit.karate.StepDefs;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author vmchukky
 */
public class VarLists {

    private final ObservableList<Var> varList;
    private final ObservableList<Var> requestVarList;
    private final ObservableList<Var> responseVarList;

    public VarLists(StepDefs stepDefs) {
        if (stepDefs == null) {
            varList = requestVarList = responseVarList = FXCollections.emptyObservableList();
        } else {
            ScriptValueMap map = stepDefs.context.getVars();
            Var var;
            List<Var> vars = new ArrayList();
            List<Var> requestVars = new ArrayList();
            List<Var> responseVars = new ArrayList();
            String key;
            for (Map.Entry<String, ScriptValue> entry : map.entrySet()) {
                key = entry.getKey();
                var = new Var(key, entry.getValue());
                if (key.startsWith(ScriptValueMap.VAR_REQUEST)) {
                    requestVars.add(var);
                } else if (key.startsWith(ScriptValueMap.VAR_RESPONSE)) {
                    responseVars.add(var);
                } else {
                    vars.add(var);
                }
            }
            varList = FXCollections.observableList(vars);
            requestVarList = FXCollections.observableList(requestVars);
            responseVarList = FXCollections.observableList(responseVars);
        }
    }

    public ObservableList<Var> getVarList() {
        return varList;
    }

    public ObservableList<Var> getRequestVarList() {
        return requestVarList;
    }

    public ObservableList<Var> getResponseVarList() {
        return responseVarList;
    }
}
