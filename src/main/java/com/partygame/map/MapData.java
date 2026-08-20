package com.partygame.map;

import com.google.gson.JsonObject;

// 一张地图：程序生成房、内置结构模板（随 mod 打包）或自建结构模板地图
public record MapData(String name, MapType type, String template, int radius, boolean builtin) {

    public enum MapType { PROCEDURAL, TEMPLATE }

    // 内置"程序生成房"：地图列表中的固定选项，名字不可被占用
    public static final MapData PROCEDURAL = new MapData("procedural", MapType.PROCEDURAL, "", 0);

    // 便捷构造：默认非内置（自建地图）
    public MapData(String name, MapType type, String template, int radius) {
        this(name, type, template, radius, false);
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("name", name);
        o.addProperty("type", type.name());
        o.addProperty("template", template);
        o.addProperty("radius", radius);
        o.addProperty("builtin", builtin);
        return o;
    }

    public static MapData fromJson(JsonObject o) {
        return new MapData(
                o.get("name").getAsString(),
                MapType.valueOf(o.get("type").getAsString()),
                o.get("template").getAsString(),
                o.get("radius").getAsInt(),
                // 旧配置无 builtin 字段视为自建地图
                o.has("builtin") && o.get("builtin").getAsBoolean());
    }
}
