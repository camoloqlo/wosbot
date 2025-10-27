package cl.camodev.wosbot.console.enumerable;

/**
 * Enum representing Expert Skills for prioritization
 * Each expert has 4 skills
 */
public enum ExpertSkillItem implements PrioritizableItem {
    // Cyrille's skills
    CYRILLE_SKILL_1("cyrille_skill_1", "Cyrille - Skill 1"),
    CYRILLE_SKILL_2("cyrille_skill_2", "Cyrille - Skill 2"),
    CYRILLE_SKILL_3("cyrille_skill_3", "Cyrille - Skill 3"),
    CYRILLE_SKILL_4("cyrille_skill_4", "Cyrille - Skill 4"),

    // Agnes's skills
    AGNES_SKILL_1("agnes_skill_1", "Agnes - Skill 1"),
    AGNES_SKILL_2("agnes_skill_2", "Agnes - Skill 2"),
    AGNES_SKILL_3("agnes_skill_3", "Agnes - Skill 3"),
    AGNES_SKILL_4("agnes_skill_4", "Agnes - Skill 4"),

    // Holger's skills
    HOLGER_SKILL_1("holger_skill_1", "Holger - Skill 1"),
    HOLGER_SKILL_2("holger_skill_2", "Holger - Skill 2"),
    HOLGER_SKILL_3("holger_skill_3", "Holger - Skill 3"),
    HOLGER_SKILL_4("holger_skill_4", "Holger - Skill 4"),

    // Romulus's skills
    ROMULUS_SKILL_1("romulus_skill_1", "Romulus - Skill 1"),
    ROMULUS_SKILL_2("romulus_skill_2", "Romulus - Skill 2"),
    ROMULUS_SKILL_3("romulus_skill_3", "Romulus - Skill 3"),
    ROMULUS_SKILL_4("romulus_skill_4", "Romulus - Skill 4"),

    // Baldur's skill
    BALDUR_SKILL_1("baldur_skill_1", "Baldur - Skill 1"),
    BALDUR_SKILL_2("baldur_skill_2", "Baldur - Skill 2"),
    BALDUR_SKILL_3("baldur_skill_3", "Baldur - Skill 3"),
    BALDUR_SKILL_4("baldur_skill_4", "Baldur - Skill 4"),

    // Fabian's skills
    FABIAN_SKILL_1("fabian_skill_1", "Fabian - Skill 1"),
    FABIAN_SKILL_2("fabian_skill_2", "Fabian - Skill 2"),
    FABIAN_SKILL_3("fabian_skill_3", "Fabian - Skill 3"),
    FABIAN_SKILL_4("fabian_skill_4", "Fabian - Skill 4");


    ;

    private final String identifier;
    private final String displayName;

    ExpertSkillItem(String identifier, String displayName) {
        this.identifier = identifier;
        this.displayName = displayName;
    }

    @Override
    public String getIdentifier() {
        return identifier;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }
}

