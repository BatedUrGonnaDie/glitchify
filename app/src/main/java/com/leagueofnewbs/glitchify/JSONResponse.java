package com.leagueofnewbs.glitchify;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

class JSONResponse {
    private int statusCode;
    private String json;

    JSONResponse(int statusCode, String json) {
        this.statusCode = statusCode;
        this.json = json;
    }

    int getStatusCode() {
        return statusCode;
    }

    JSONObject jsonAsObject() throws Exception {
        JSONObject object;
        try {
            object = new JSONObject(this.json);
        } catch (JSONException e) {
            object = new JSONObject();
        }
        if (object.isNull("status")) {
            object.put("status", "" + this.statusCode);
        }
        return object;
    }

    JSONArray jsonAsArray() {
        try {
            return new JSONArray(json);
        } catch (JSONException e) {
            return new JSONArray();
        }
    }
}
