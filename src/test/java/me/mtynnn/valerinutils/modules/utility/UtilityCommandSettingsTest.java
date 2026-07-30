package me.mtynnn.valerinutils.modules.utility;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UtilityCommandSettingsTest {

    @Test
    void aliasesAndGroupedCommandsUseTheirRealYamlSwitch() {
        assertEquals("smithing", UtilityModule.commandSettingKey("smithingtable"));
        assertEquals("cartography", UtilityModule.commandSettingKey("cartographytable"));
        assertEquals("gamemode", UtilityModule.commandSettingKey("gmc"));
        assertEquals("gamemode", UtilityModule.commandSettingKey("gms"));
        assertEquals("gamemode", UtilityModule.commandSettingKey("gmsp"));
        assertEquals("gamemode", UtilityModule.commandSettingKey("gma"));
        assertEquals("broadcast", UtilityModule.commandSettingKey("vubroadcast"));
        assertEquals("top", UtilityModule.commandSettingKey("vtop"));
    }

    @Test
    void directCommandsKeepTheirOwnYamlSwitch() {
        assertEquals("sell", UtilityModule.commandSettingKey("sell"));
        assertEquals("helpop", UtilityModule.commandSettingKey("helpop"));
        assertEquals("repair", UtilityModule.commandSettingKey("repair"));
    }
}
