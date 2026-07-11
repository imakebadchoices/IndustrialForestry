package forestry.apiculture;

import forestry.core.genetics.mutations.MutationConditionCave;

/**
 * @deprecated identical to {@link MutationConditionCave}, which it now extends purely so existing call
 * sites keep compiling. Both serialize as the {@code forestry:cave} mutation condition type; prefer
 * {@link MutationConditionCave} directly.
 */
@Deprecated
public class CaveMutationCondition extends MutationConditionCave {
}
