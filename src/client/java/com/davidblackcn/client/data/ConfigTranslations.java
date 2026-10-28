package com.davidblackcn.client.data;

import com.google.gson.JsonObject;

/** UI translations generated together with the block and item language files. */
final class ConfigTranslations {
    static void add(JsonObject en, JsonObject zh) {
        String[][] labels = {
            {"title", "Buildup Groundscape", "Buildup Groundscape 配置"},
            {"terrain", "Terrain", "地表与坡面"}, {"materials", "Materials", "材质设置"},
            {"leaves", "Leaf patches", "树叶薄层"}, {"types", "Tree species", "树种设置"},
            {"source_local", "Editing local instance defaults (all local worlds).", "正在编辑本机配置（应用于本机各存档）。"},
            {"source_server", "Editing this server's configuration. Host / administrator access.", "正在编辑当前服务器配置。你拥有主机或管理员权限。"},
            {"source_readonly", "Server configuration: read only. Administrator permission required to save.", "服务器配置：只读。保存修改需要管理员权限。"},
            {"new_chunks", "Saved settings apply to future decoration of new chunks. Existing terrain is never regenerated; density may differ at old/new borders.", "保存后用于后续新区块装饰；不会重新铺设旧地形。新旧区域交界处密度可能不同。"},
            {"leaves_global_off", "Current status: blocked by the global generation switch.", "当前状态：生成总开关关闭，树叶不会生成。"},
            {"leaves_off", "Current status: leaf generation disabled.", "当前状态：树叶生成关闭。"},
            {"leaves_on", "Current status: leaf generation enabled (subject to limits and safety checks).", "当前状态：树叶生成开启，仍受数量与安全检查限制。"},
            {"leaf_density", "Coverage controls candidates, not final coverage. Plants and terrain patches take priority; zero coverage or cap disables leaves.", "覆盖率是候选比例，不是最终覆盖率。植物和地表薄层优先；覆盖率或数量上限为零时不生成。"},
            {"enabled", "Natural generation", "自然生成总开关"},
            {"density", "Terrain coverage density", "地表覆盖密度"},
            {"maxLayers", "Maximum terrain layers", "地表生成最大层数"},
            {"slopeInfluence", "Slope influence", "坡面影响"},
            {"flatCoverage", "Flat terrain coverage", "平地覆盖"},
            {"layerScale", "Thickness multiplier", "厚度倍率"},
            {"heightVariation", "Height variation", "高度扰动"},
            {"leafPatches.enabled", "Generate leaf patches", "生成树叶薄层"},
            {"leafPatches.coverage", "Leaf candidate coverage", "树叶候选覆盖率"},
            {"leafPatches.maxLayers", "Maximum leaf layers", "树叶生成最大层数"},
            {"leafPatches.searchHeight", "Canopy search height", "树冠搜索高度"},
            {"leafPatches.maxPerChunk", "Maximum leaf positions per chunk", "每区块树叶位置数量上限"},
            {"enabled.description", "Enables terrain and leaf generation. Applies to new chunks only.", "控制地表与树叶自然生成。只影响新区块。"},
            {"density.description", "Terrain sampling density, 0–0.35. Zero leaves independently enabled leaf generation active.", "地表采样密度，范围 0–0.35。设为零不会关闭独立启用的树叶生成。"},
            {"maxLayers.description", "Natural terrain cap, 1–7. Manual stacking still supports 8 layers.", "自然地表层数上限，范围 1–7。手动堆叠仍支持 8 层。"},
            {"slopeInfluence.description", "How strongly coverage favors slopes, 0–1.", "覆盖分布对坡面的偏向程度，范围 0–1。"},
            {"flatCoverage.description", "Relative coverage retained on flat terrain, 0–1.", "平地保留的相对覆盖程度，范围 0–1。"},
            {"layerScale.description", "Scales terrain thickness, 0.5–1.5, respecting the layer cap.", "缩放地表薄层厚度，范围 0.5–1.5，仍受最大层数限制。"},
            {"heightVariation.description", "Coordinate-based natural height variation, 0–1.", "基于坐标的自然高度扰动，范围 0–1。"},
            {"leafPatches.enabled.description", "Independent leaf switch; also requires the global generation switch.", "树叶独立开关；还需要开启自然生成总开关。"},
            {"leafPatches.coverage.description", "Candidate fraction, 0–0.04. Safety and canopy checks reduce actual coverage.", "候选比例，范围 0–0.04。树冠与安全检查会进一步降低实际覆盖。"},
            {"leafPatches.maxLayers.description", "Natural leaf height cap, 1–2; manual stacking supports 8.", "自然树叶高度上限，范围 1–2；手动支持 8 层。"},
            {"leafPatches.searchHeight.description", "Maximum vertical canopy search distance, 1–32 blocks.", "向上寻找对应树冠的最大距离，范围 1–32 格。"},
            {"leafPatches.maxPerChunk.description", "Maximum placed positions, 0–32. Zero disables leaf generation.", "单个区块放置位置的最大数量，范围 0–32。零表示不生成。"},
            {"material_enabled", "Enable this material's natural patches.", "允许生成该材质的自然薄层。"},
            {"material_thicknessScale", "Material-specific thickness multiplier, 0.5–1.5.", "该材质的独立厚度倍率，范围 0.5–1.5。"},
            {"type_description", "Allow patches beneath this natural tree species. Does not alter manually placed blocks.", "允许在该树种的自然树冠下生成薄层。不影响手动放置。"},
            {"saved", "Configuration saved and applied", "配置已保存并应用"},
            {"waiting", "Waiting for server configuration…", "正在等待服务器配置……"},
            {"error", "Configuration was not confirmed", "配置未确认生效"},
            {"local_failed", "Save failed or another editor changed the configuration. The active configuration was preserved. Reopen and retry.", "保存失败，或配置已被其他编辑者修改。保留当前生效配置，请重新打开后重试。"},
            {"unavailable", "Server configuration channel unavailable. No local fallback is applied to a remote server.", "服务器配置通道不可用。不会用本机配置替代服务器配置。"},
            {"timeout", "No server acknowledgement. The request may have been applied; reopen to inspect the current values before retrying.", "未收到服务器确认。请求可能已生效，请重新打开检查实际值后再重试。"},
            {"error_permission", "Administrator permission required. Nothing was changed.", "需要管理员权限，配置未修改。"},
            {"error_conflict", "Another editor or reload changed the configuration. Reopen to load the latest values.", "其他编辑者或重载操作已改变配置，请重新打开加载最新值。"},
            {"error_invalid", "Invalid settings were rejected; previous settings remain active.", "配置不合法，已拒绝修改并保留原配置。"},
            {"error_io", "Server could not save the file. Previous settings remain active; consult the server log.", "服务器无法保存文件，原配置仍生效，请查看服务器日志。"},
            {"reloaded", "Groundscape configuration reloaded; future new chunks use the new settings.", "Groundscape 配置已重载，后续新区块使用新配置。"},
            {"reload_failed", "Reload failed; active configuration preserved. Check the file and server log.", "重载失败，保留当前生效配置。请检查配置文件和服务器日志。"}
        };
        for (String[] entry : labels) put(en, zh, entry[0], entry[1], entry[2]);
        String[][] materials = {{"sand", "Sand", "沙子"}, {"dirt", "Dirt", "泥土"}, {"coarse_dirt", "Coarse dirt", "砂土"}, {"red_sand", "Red sand", "红沙"}, {"gravel", "Gravel", "沙砾"}, {"rooted_dirt", "Rooted dirt", "缠根泥土"}};
        for (String[] m : materials) {
            put(en, zh, "materials." + m[0] + ".enabled", m[1] + ": generation", m[2] + "：自然生成");
            put(en, zh, "materials." + m[0] + ".thicknessScale", m[1] + ": thickness", m[2] + "：厚度倍率");
        }
        String[] names = {"橡树", "云杉", "白桦", "丛林", "金合欢", "深色橡树", "红树", "樱花", "苍白橡树", "杜鹃", "盛开的杜鹃", "红色杨树", "橙色杨树", "黄色杨树"};
        for (var family : com.davidblackcn.registry.LeafFamily.values())
            put(en, zh, "leafPatches.types." + family.id(), family.id().replace('_', ' '), names[family.ordinal()]);
    }
    private static void put(JsonObject en, JsonObject zh, String key, String english, String chinese) {
        en.addProperty("groundscape.config." + key, english);
        zh.addProperty("groundscape.config." + key, chinese);
    }
}
