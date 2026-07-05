# Categories and Merchants

## Category list

`CategoriesViewModel` loads Room categories and combines each category with transaction totals, merchant counts, colors, and icons. `CategoriesFragment` renders the list and opens category detail or category transaction screens.

## Automatic categorization

```text
New transaction
  -> MerchantRuleEngine.categorize()
  -> locate or create CategoryEntity
  -> locate or create MerchantEntity
  -> copy merchant category_id to TransactionEntity
```

Rules come from `assets/merchant_rules.json`. Unknown merchants fall back to the system `Other` category.

## Manual category change

A merchant category change must update:

1. `merchants.category_id`
2. Every active transaction with the normalized merchant name
3. Alias display caches
4. Dependent screens through a data-change event

`MerchantCategoryOperations` centralizes most of this work, although some UI paths still call repository and DAO methods directly.

## Category deletion

User categories are reassigned before deletion. The repository includes transaction category reassignment queries, which must remain paired with merchant reassignment so aggregate joins do not lose rows.

## Merchant inclusion and deletion

- `is_excluded_from_expense_tracking` removes a merchant from expense totals without deleting transactions.
- `is_deleted` hides a merchant and supports suppressing future SMS-derived records.
- Aliases separate normalized matching from the name displayed in the UI.

## Key sources

- `ui/categories/`
- `parsing/engine/MerchantRuleEngine.kt`
- `utils/CategoryManager.kt`
- `utils/MerchantAliasManager.kt`
- `data/repository/internal/MerchantCategoryOperations.kt`
- `data/dao/MerchantDao.kt`
