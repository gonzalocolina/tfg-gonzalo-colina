package com.tfg.gonzalo.backend.service;

import com.tfg.gonzalo.backend.model.enums.VillainRuleType;
import org.springframework.stereotype.Service;

@Service
public class VillainFormulaService {

    public int computeVillainHp(VillainRuleType ruleType, Integer ruleValue, Integer configuredPlayerCount) {
        int safeRuleValue = normalizeRuleValue(ruleValue);
        int safePlayerCount = configuredPlayerCount == null ? 0 : Math.max(0, configuredPlayerCount);
        if (ruleType == VillainRuleType.FIXED_VALUE) {
            return safeRuleValue;
        }
        return safePlayerCount * safeRuleValue;
    }

    public int computeThreatTarget(VillainRuleType ruleType, Integer ruleValue, Integer groupCount) {
        int safeRuleValue = normalizeRuleValue(ruleValue);
        int safeGroupCount = groupCount == null ? 0 : Math.max(0, groupCount);
        if (ruleType == VillainRuleType.FIXED_VALUE) {
            return safeRuleValue;
        }
        return safeGroupCount * safeRuleValue;
    }

    private int normalizeRuleValue(Integer value) {
        if (value == null) {
            return 0;
        }
        return Math.max(0, value);
    }
}