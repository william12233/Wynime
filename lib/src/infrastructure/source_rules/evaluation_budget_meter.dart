import '../../domain/models/source_rule_program.dart';
import '../../domain/models/source_security_policy.dart';

final class EvaluationBudgetMeter {
  EvaluationBudgetMeter(this.budget);

  final SourceResourceBudget budget;
  int consumedSteps = 0;
  int selectorMatches = 0;

  void consumeStep([int count = 1]) {
    consumedSteps += count;
    if (consumedSteps > budget.maxEvaluationSteps) {
      throw SourceRuleSecurityException(
        'evaluation_budget_exceeded',
        'Evaluation step budget was exceeded.',
      );
    }
  }

  void addSelectorMatches(int count) {
    selectorMatches += count;
    if (selectorMatches > budget.maxSelectorMatches) {
      throw SourceRuleSecurityException(
        'selector_match_budget_exceeded',
        'Selector match budget was exceeded.',
      );
    }
  }
}
