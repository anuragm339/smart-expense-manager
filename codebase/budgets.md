# Budget Flow

## Budget model

`BudgetEntity` supports overall and category budgets. A null `category_id` represents the overall monthly budget; a non-null ID represents a category budget. Rows include period dates, active state, amount, and creation time.

## Overall monthly budget

```text
BudgetGoalsFragment
  -> BudgetGoalsViewModel
  -> ExpenseRepository
  -> BudgetDao.getOverallMonthlyBudget()
  -> monthly spending query
  -> progress and remaining amount
```

Saving an overall budget deactivates the previous active monthly row and inserts a new one. This preserves history while exposing one current budget.

## Category budgets

The ViewModel loads categories, active category budgets, and category spending. It calculates utilization and status for each category and supports updating individual limits.

## Dashboard relationship

Dashboard balance calculations and the dedicated budget screen both query transaction spending. Their date boundaries and exclusion behavior must stay aligned to avoid conflicting totals.

## Key sources

- `ui/profile/BudgetGoalsFragment.kt`
- `ui/profile/BudgetGoalsViewModel.kt`
- `data/entities/BudgetEntity.kt`
- `data/dao/BudgetDao.kt`
- `domain/usecase/dashboard/GetBudgetStatusUseCase.kt`
