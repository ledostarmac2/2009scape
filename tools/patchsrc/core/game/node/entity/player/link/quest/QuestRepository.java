package core.game.node.entity.player.link.quest;

import content.data.Quests;
import core.api.ContentAPIKt;
import core.game.node.entity.player.Player;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

public final class QuestRepository {
    private static final Map<Quests, Quest> QUESTS = new TreeMap<Quests, Quest>();
    private static final Object[][] QUEST_TAB_ROWS = new Object[][] {
        {5, "Learning the Ropes"}, {6, "Christmas Event"}, {7, "Chasing Jack Frost"}, {8, "Chasing Jack Frost"},
        {9, "Christmas Quest"}, {10, "Myths of the White Lands"}, {11, "Myths of the White Lands"},
        {13, "Black Knights' Fortress"}, {14, "Cook's Assistant"}, {15, "Demon Slayer"}, {16, "Doric's Quest"},
        {17, "Dragon Slayer"}, {18, "Ernest the Chicken"}, {19, "Goblin Diplomacy"}, {20, "Imp Catcher"},
        {21, "The Knight's Sword"}, {22, "Pirate's Treasure"}, {23, "Prince Ali Rescue"}, {24, "The Restless Ghost"},
        {25, "Romeo & Juliet"}, {26, "Rune Mysteries"}, {27, "Sheep Shearer"}, {28, "Shield of Arrav"},
        {29, "Vampire Slayer"}, {30, "Witch's Potion"}, {32, "Animal Magnetism"}, {33, "Between a Rock..."},
        {34, "Big Chompy Bird Hunting"}, {35, "Biohazard"}, {36, "Cabin Fever"}, {37, "Clock Tower"},
        {38, "Contact!"}, {39, "Zogre Flesh Eaters"}, {40, "Creature of Fenkenstrain"}, {41, "Darkness of Hallowvale"},
        {42, "Death to the Dorgeshuun"}, {43, "Death Plateau"}, {44, "Desert Treasure"}, {45, "Devious Minds"},
        {46, "The Dig Site"}, {47, "Druidic Ritual"}, {48, "Dwarf Cannon"}, {49, "Eadgar's Ruse"},
        {50, "Eagles' Peak"}, {51, "Elemental Workshop I"}, {52, "Elemental Workshop II"}, {53, "Enakhra's Lament"},
        {54, "Enlightened Journey"}, {55, "The Eyes of Glouphrie"}, {56, "Fairytale I - Growing Pains"},
        {57, "Fairytale II - Cure a Queen"}, {58, "Family Crest"}, {59, "The Feud"}, {60, "Fight Arena"},
        {61, "Fishing Contest"}, {62, "Forgettable Tale..."}, {63, "The Fremennik Trials"}, {64, "Waterfall Quest"},
        {65, "Garden of Tranquillity"}, {66, "Gertrude's Cat"}, {67, "Ghosts Ahoy"}, {68, "The Giant Dwarf"},
        {69, "The Golem"}, {70, "The Grand Tree"}, {71, "The Hand in the Sand"}, {72, "Haunted Mine"},
        {73, "Hazeel Cult"}, {74, "Heroes' Quest"}, {75, "Holy Grail"}, {76, "Horror from the Deep"},
        {77, "Icthlarin's Little Helper"}, {78, "In Aid of the Myreque"}, {79, "In Search of the Myreque"},
        {80, "Jungle Potion"}, {81, "Legends' Quest"}, {82, "Lost City"}, {83, "The Lost Tribe"},
        {84, "Lunar Diplomacy"}, {85, "Making History"}, {86, "Merlin's Crystal"}, {87, "Monkey Madness"},
        {88, "Monk's Friend"}, {89, "Mountain Daughter"}, {90, "Mourning's Ends Part I"},
        {91, "Mourning's Ends Part II"}, {92, "Murder Mystery"}, {93, "My Arm's Big Adventure"},
        {94, "Nature Spirit"}, {95, "Observatory Quest"}, {96, "One Small Favour"}, {97, "Plague City"},
        {98, "Priest in Peril"}, {99, "Rag and Bone Man"}, {100, "Ratcatchers"}, {101, "Recipe for Disaster"},
        {102, "Recruitment Drive"}, {103, "Regicide"}, {104, "Roving Elves"}, {105, "Royal Trouble"},
        {106, "Rum Deal"}, {107, "Scorpion Catcher"}, {108, "Sea Slug"}, {109, "The Slug Menace"},
        {110, "Shades of Mort'ton"}, {111, "Shadow of the Storm"}, {112, "Sheep Herder"}, {113, "Shilo Village"},
        {114, "A Soul's Bane"}, {115, "Spirits of the Elid"}, {116, "Swan Song"}, {117, "Tai Bwo Wannai Trio"},
        {118, "A Tail of Two Cats"}, {119, "Tears of Guthix"}, {120, "Temple of Ikov"}, {121, "Throne of Miscellania"},
        {122, "The Tourist Trap"}, {123, "Witch's House"}, {124, "Tree Gnome Village"}, {125, "Tribal Totem"},
        {126, "Troll Romance"}, {127, "Troll Stronghold"}, {128, "Underground Pass"}, {129, "Wanted!"},
        {130, "Watchtower"}, {131, "Cold War"}, {132, "The Fremennik Isles"}, {133, "Tower of Life"},
        {134, "The Great Brain Robbery"}, {135, "What Lies Below"}, {136, "Olaf's Quest"},
        {137, "Another Slice of H.A.M."}, {138, "Dream Mentor"}, {139, "Grim Tales"}, {140, "King's Ransom"},
        {141, "The Path of Glouphrie"}, {142, "Back to my Roots"}, {143, "Land of the Goblins"},
        {144, "Dealing with Scabaras"}, {145, "Wolf Whistle"}, {146, "As a First Resort..."},
        {147, "Catapult Construction"}, {148, "Kennith's Concerns"}, {149, "Legacy of Seergaze"},
        {150, "Perils of Ice Mountain"}, {151, "TokTz-Ket-Dill"}, {152, "Smoking Kills"}, {153, "Rocking Out"},
        {154, "Spirit of Summer"}, {155, "Meeting History"}, {156, "All Fired Up"}, {157, "Summer's End"},
        {158, "Defender of Varrock"}, {159, "Swept Away"}, {160, "While Guthix Sleeps"}, {161, "In Pyre Need"},
        {162, "Myths of the White Lands"}
    };

    private final Map<Integer, Integer> quests = new HashMap<Integer, Integer>();
    private final Player player;
    private int points;

    public QuestRepository(Player player) {
        this.player = player;
        for (Quest quest : QUESTS.values()) {
            quests.put(Integer.valueOf(quest.getIndex()), Integer.valueOf(0));
        }
    }

    public void parse(JSONObject data) {
        points = Integer.parseInt(data.get("points").toString());
        JSONArray stages = (JSONArray) data.get("questStages");
        stages.forEach(this::parseStage);
        syncPoints();
    }

    public void syncronizeTab(Player player) {
        ContentAPIKt.setVarp(player, 101, points);
        for (Quest quest : QUESTS.values()) {
            int[] config = quest.getConfig(player, getStage(quest));
            if (config.length == 3) {
                ContentAPIKt.setVarbit(player, config[1], config[2]);
            } else if (config.length >= 2) {
                ContentAPIKt.setVarp(player, config[0], config[1]);
            }
            quest.updateVarps(player);
        }
        greenQuestTab(player);
    }

    public void setStage(Quest quest, int stage) {
        int oldStage = getStage(quest);
        if (oldStage < stage) {
            quests.put(Integer.valueOf(quest.getIndex()), Integer.valueOf(stage));
        }
    }

    public void setStageNonmonotonic(Quest quest, int stage) {
        quests.put(Integer.valueOf(quest.getIndex()), Integer.valueOf(stage));
    }

    public void incrementPoints(int points) {
        this.points += points;
    }

    public void dockPoints(int points) {
        this.points -= points;
    }

    public void syncPoints() {
        int total = 0;
        for (Quest quest : QUESTS.values()) {
            if (getStage(quest) >= 100) {
                total += quest.getQuestPoints();
            }
        }
        points = total;
    }

    public int getAvailablePoints() {
        int total = 0;
        for (Quest quest : QUESTS.values()) {
            total += quest.getQuestPoints();
        }
        return total;
    }

    public Quest forButtonId(int buttonId) {
        for (Quest quest : QUESTS.values()) {
            if (quest.getButtonId() == buttonId) {
                return quest;
            }
        }
        return null;
    }

    public Quest forIndex(int index) {
        for (Quest quest : QUESTS.values()) {
            if (quest.getIndex() == index) {
                return quest;
            }
        }
        return null;
    }

    public boolean hasCompletedAll() {
        return getPoints() >= getAvailablePoints();
    }

    public boolean isComplete(Quests quest) {
        Quest q = getQuest(quest);
        return q == null || q.getStage(player) >= 100;
    }

    public boolean hasStarted(Quests quest) {
        Quest q = getQuest(quest);
        return q == null || q.getStage(player) > 0;
    }

    public int getStage(Quests quest) {
        Quest q = QUESTS.get(quest);
        return q == null ? 100 : getStage(q);
    }

    public int getStage(Quest quest) {
        Integer stage = quests.get(Integer.valueOf(quest.getIndex()));
        return stage == null ? 0 : stage.intValue();
    }

    public Quest getQuest(Quests quest) {
        return QUESTS.get(quest);
    }

    public int getPoints() {
        return points;
    }

    public Player getPlayer() {
        return player;
    }

    public static void register(Quest quest) {
        QUESTS.put(quest.getQuest(), quest);
    }

    public static Map<Quests, Quest> getQuests() {
        return QUESTS;
    }

    public Map<Integer, Integer> getQuestList() {
        return quests;
    }

    private void parseStage(Object item) {
        JSONObject stage = (JSONObject) item;
        quests.put(Integer.valueOf(Integer.parseInt(stage.get("questId").toString())),
            Integer.valueOf(Integer.parseInt(stage.get("questStage").toString())));
    }

    private static void greenQuestTab(Player player) {
        for (Object[] row : QUEST_TAB_ROWS) {
            player.getPacketDispatch().sendString("<col=00ff00>" + row[1], 274, ((Integer) row[0]).intValue());
        }
    }
}
