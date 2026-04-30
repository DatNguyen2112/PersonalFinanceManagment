package BankingSystem.Services;

import BankingSystem.DTO.BankingDTO;
import BankingSystem.Entity.*;
import BankingSystem.Repositories.*;
import org.apache.kafka.common.errors.DuplicateResourceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PersonalFinanceServiceTest {

    @Mock
    private BudgetRepository budgetRepository;

    @Mock
    private ExpenseRepository expenseRepository;

    @Mock
    private SavingsGoalRepository savingsGoalRepository;

    @Mock
    private SpendingCategoryRepository categoryRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private PersonalFinanceService service;

    private User testUser;
    private SpendingCategory testCategory;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
            .id(1L)
            .username("testuser")
            .email("test@example.com")
            .firstName("Test")
            .lastName("User")
            .build();

        testCategory = SpendingCategory.builder()
            .id(1L)
            .name("Ăn uống")
            .icon("🍔")
            .colorHex("#FF6B6B")
            .type(CategoryType.EXPENSE)
            .systemDefault(true)
            .user(testUser)
            .build();
    }

    @Test
    void createBudget_success() {
        BankingDTO.BudgetRequest req = BankingDTO.BudgetRequest.builder()
            .categoryId(1L)
            .limitAmount(new BigDecimal("1000.00"))
            .year(2026)
            .month(1)
            .alertThreshold(80)
            .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(testCategory));
        when(budgetRepository.findByUserIdAndCategoryIdAndYearAndMonth(1L, 1L, 2026, 1))
            .thenReturn(Optional.empty());

        Budget savedBudget = Budget.builder()
            .id(1L)
            .user(testUser)
            .category(testCategory)
            .limitAmount(new BigDecimal("1000.00"))
            .spentAmount(BigDecimal.ZERO)
            .year(2026)
            .month(1)
            .alertThreshold(80)
            .status(BudgetStatus.ACTIVE)
            .build();

        when(budgetRepository.save(any(Budget.class))).thenReturn(savedBudget);

        BankingDTO.BudgetResponse response = service.createBudget(1L, req);

        assertNotNull(response);
        assertEquals(new BigDecimal("1000.00"), response.getLimitAmount());
        assertEquals(BudgetStatus.ACTIVE, response.getStatus());
        verify(budgetRepository, times(1)).save(any(Budget.class));
    }

    @Test
    void createBudget_whenDuplicate_throwsDuplicateResourceException() {
        BankingDTO.BudgetRequest req = BankingDTO.BudgetRequest.builder()
            .categoryId(1L)
            .limitAmount(new BigDecimal("1000.00"))
            .year(2026)
            .month(1)
            .build();

        Budget existingBudget = Budget.builder()
            .id(1L)
            .user(testUser)
            .category(testCategory)
            .limitAmount(new BigDecimal("1000.00"))
            .year(2026)
            .month(1)
            .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(testCategory));
        when(budgetRepository.findByUserIdAndCategoryIdAndYearAndMonth(1L, 1L, 2026, 1))
            .thenReturn(Optional.of(existingBudget));

        assertThrows(DuplicateResourceException.class, () -> service.createBudget(1L, req));
    }

    @Test
    void createExpense_success() {
        BankingDTO.ExpenseRequest req = BankingDTO.ExpenseRequest.builder()
            .categoryId(1L)
            .amount(new BigDecimal("50.00"))
            .type(ExpenseType.EXPENSE)
            .expenseDate(LocalDate.now())
            .note("Test expense")
            .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(testCategory));

        Expense savedExpense = Expense.builder()
            .id(1L)
            .user(testUser)
            .category(testCategory)
            .amount(new BigDecimal("50.00"))
            .type(ExpenseType.EXPENSE)
            .expenseDate(LocalDate.now())
            .note("Test expense")
            .build();

        when(expenseRepository.save(any(Expense.class))).thenReturn(savedExpense);

        BankingDTO.ExpenseResponse response = service.createExpense(1L, req);

        assertNotNull(response);
        assertEquals(new BigDecimal("50.00"), response.getAmount());
        assertEquals(ExpenseType.EXPENSE, response.getType());
        verify(expenseRepository, times(1)).save(any(Expense.class));
    }

    @Test
    void contributeToGoal_whenTargetReached_setsStatusCompleted() {
        SavingsGoal goal = SavingsGoal.builder()
            .id(1L)
            .user(testUser)
            .name("Buy a car")
            .targetAmount(new BigDecimal("10000.00"))
            .savedAmount(new BigDecimal("9500.00"))
            .status(GoalStatus.IN_PROGRESS)
            .build();

        when(savingsGoalRepository.findById(1L)).thenReturn(Optional.of(goal));
        when(savingsGoalRepository.save(any(SavingsGoal.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BankingDTO.SavingsGoalResponse response = service.contributeToGoal(1L, 1L, new BigDecimal("500.00"));

        assertEquals(GoalStatus.COMPLETED, response.getStatus());
        assertEquals(new BigDecimal("10000.00"), response.getSavedAmount());
        verify(kafkaTemplate, times(1)).send(eq("banking.notifications"), any(), any());
    }

    @Test
    void createCategory_success() {
        BankingDTO.CategoryRequest req = BankingDTO.CategoryRequest.builder()
            .name("Du lịch")
            .icon("✈️")
            .colorHex("#FF6B6B")
            .type(CategoryType.EXPENSE)
            .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(categoryRepository.existsByNameAndUserId("Du lịch", 1L)).thenReturn(false);

        SpendingCategory savedCategory = SpendingCategory.builder()
            .id(2L)
            .user(testUser)
            .name("Du lịch")
            .icon("✈️")
            .colorHex("#FF6B6B")
            .type(CategoryType.EXPENSE)
            .systemDefault(false)
            .build();

        when(categoryRepository.save(any(SpendingCategory.class))).thenReturn(savedCategory);

        BankingDTO.CategoryResponse response = service.createCategory(1L, req);

        assertNotNull(response);
        assertEquals("Du lịch", response.getName());
        verify(categoryRepository, times(1)).save(any(SpendingCategory.class));
    }
}

