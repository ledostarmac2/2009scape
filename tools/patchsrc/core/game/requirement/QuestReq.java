package core.game.requirement;

import core.game.node.entity.player.Player;
import core.game.node.entity.player.link.quest.Quest;
import core.game.node.entity.player.link.quest.QuestRepository;
import java.util.ArrayList;
import java.util.List;
import kotlin.Pair;
import kotlin.jvm.internal.DefaultConstructorMarker;

public class QuestReq implements Requirement {
    private final QuestRequirements questReq;
    private final int stageRequired;

    public QuestReq(QuestRequirements questReq, int stageRequired) {
        this.questReq = questReq;
        this.stageRequired = stageRequired;
    }

    public QuestReq(QuestRequirements questReq, int stageRequired, int mask, DefaultConstructorMarker marker) {
        this(questReq, (mask & 2) != 0 ? 100 : stageRequired);
    }

    public final QuestRequirements getQuestReq() {
        return questReq;
    }

    public final int getStageRequired() {
        return stageRequired;
    }

    public Pair<Boolean, List<Requirement>> evaluate(Player player) {
        Quest quest = QuestRepository.getQuests().get(questReq.getQuest());
        ArrayList<Requirement> requirements = new ArrayList<Requirement>();
        if (quest == null) {
            return new Pair<Boolean, List<Requirement>>(Boolean.TRUE, requirements);
        }

        boolean passed = quest.getStage(player) >= stageRequired;
        if (!passed) {
            requirements.add(this);
        }
        requirements.add(new QPCumulative(quest.getQuestPoints()));
        return new Pair<Boolean, List<Requirement>>(Boolean.valueOf(passed), requirements);
    }
}
