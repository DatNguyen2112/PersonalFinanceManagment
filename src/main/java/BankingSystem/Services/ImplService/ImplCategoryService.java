package BankingSystem.Services.ImplService;

import BankingSystem.Entity.SpendingCategory;

public interface ImplCategoryService {
    SpendingCategory autoClassify(String content);
}
