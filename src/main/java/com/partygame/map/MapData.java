package com.partygame.map;

import com.google.gson.JsonObject;

// 一张地图：程序生成房或自建结构模板地图
public record MapData(String name, MapType type, String template, int radius) {

    public enum MapType { PROCEDURAL, TEMPLATE }

    // 内置"程序生成房"：地图列表中的固定选项，名字不可被占用
    public static final MapData PROCEDURAL = new MapData("procedural", MapType.PROCEDURAL, "", 0);

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("name", name);
        o.addProperty("type", type.name());
        o.addProperty("template", template);
        o.addProperty("radius", radius);
        return o;
    }

    public static MapData fromJson(JsonObject o) {
        return new MapData(
                o.get("name").getAsString(),
                MapType.valueOf(o.get("type").getAsString()),
                o.get("template").getAsString(),
                o.get("radius").getAsInt());
    }
}
