package BankingSystem.API;

import BankingSystem.DTO.BankingDTO;
import BankingSystem.Entity.*;
import BankingSystem.Repositories.*;
import BankingSystem.TestcontainersConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class PersonalFinanceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SpendingCategoryRepository categoryRepository;

    @Autowired
    private BudgetRepository budgetRepository;

    @Autowired
    private ExpenseRepository expenseRepository;

    @Autowired
    private SavingsGoalRepository savingsGoalRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private String authToken;
    private Long userId;

    @BeforeEach
    void setUp() throws Exception {
        // Clean up
        expenseRepository.deleteAll();
        budgetRepository.deleteAll();
        savingsGoalRepository.deleteAll();
        userRepository.deleteAll();

        // Create test user
        User user = User.builder()
            .username("testuser")
            .email("test@example.com")
            .password(passwordEncoder.encode("password123"))
            .plainTextPassword("password123")
            .firstName("Test")
            .lastName("User")
            .role(User.UserRole.CUSTOMER)
            .enabled(true)
            .build();
        user = userRepository.save(user);
        userId = user.getId();

        // Login and get token
        BankingDTO.LoginRequest loginReq = BankingDTO.LoginRequest.builder()
            .username("testuser")
            .password("password123")
            .build();

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(loginReq)))
            .andExpect(status().isOk())
            .andReturn();

        String responseBody = loginResult.getResponse().getContentAsString();
        BankingDTO.AuthResponse authResponse = objectMapper.readValue(responseBody, BankingDTO.AuthResponse.class);
        authToken = authResponse.getAccessToken();
    }

    @Test
    void testCreateAndRetrieveCategories() throws Exception {
        BankingDTO.CategoryRequest req = BankingDTO.CategoryRequest.builder()
            .name("Du lịch")
            .icon("✈️")
            .colorHex("#FF6B6B")
            .type(CategoryType.EXPENSE)
            .build();

        mockMvc.perform(post("/api/v1/finance/categories")
            .header("Authorization", "Bearer " + authToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Du lịch"));

        mockMvc.perform(get("/api/v1/finance/categories")
            .header("Authorization", "Bearer " + authToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(10))); // 9 system default + 1 custom
    }

    @Test
    void testCreateBudgetAndAddExpense() throws Exception {
        // Get default category
        var categories = categoryRepository.findByUserIdOrSystemDefaultTrue(userId);
        Long categoryId = categories.stream()
            .filter(c -> "Ăn uống".equals(c.getName()))
            .findFirst()
            .map(SpendingCategory::getId)
            .orElseThrow();

        // Create budget
        BankingDTO.BudgetRequest budgetReq = BankingDTO.BudgetRequest.builder()
            .categoryId(categoryId)
            .limitAmount(new BigDecimal("1000.00"))
            .year(2026)
            .month(4)
            .alertThreshold(80)
            .build();

        mockMvc.perform(post("/api/v1/finance/budgets")
            .header("Authorization", "Bearer " + authToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(budgetReq)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.limitAmount").value(1000.00))
            .andExpect(jsonPath("$.status").value("ACTIVE"));

        // Add expense
        BankingDTO.ExpenseRequest expenseReq = BankingDTO.ExpenseRequest.builder()
            .categoryId(categoryId)
            .amount(new BigDecimal("500.00"))
            .type(ExpenseType.EXPENSE)
            .expenseDate(LocalDate.of(2026, 4, 15))
            .note("Lunch with colleagues")
            .build();

        mockMvc.perform(post("/api/v1/finance/expenses")
            .header("Authorization", "Bearer " + authToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(expenseReq)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.amount").value(500.00));

        // Check if budget was updated
        mockMvc.perform(get("/api/v1/finance/budgets?year=2026&month=4")
            .header("Authorization", "Bearer " + authToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].spentAmount").value(500.00))
            .andExpect(jsonPath("$[0].usagePercent").value(50.0));
    }

    @Test
    void testCreateAndContributeSavingsGoal() throws Exception {
        BankingDTO.SavingsGoalRequest goalReq = BankingDTO.SavingsGoalRequest.builder()
            .name("Buy a house")
            .targetAmount(new BigDecimal("100000.00"))
            .targetDate(LocalDate.of(2030, 1, 1))
            .description("Save for a new house")
            .build();

        MvcResult createResult = mockMvc.perform(post("/api/v1/finance/goals")
            .header("Authorization", "Bearer " + authToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(goalReq)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Buy a house"))
            .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
            .andReturn();

        String responseBody = createResult.getResponse().getContentAsString();
        BankingDTO.SavingsGoalResponse goalResponse = objectMapper.readValue(responseBody, BankingDTO.SavingsGoalResponse.class);
        Long goalId = goalResponse.getId();

        // Contribute to goal
        mockMvc.perform(post("/api/v1/finance/goals/{goalId}/contribute?amount=50000.00", goalId)
            .header("Authorization", "Bearer " + authToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.savedAmount").value(50000.00))
            .andExpect(jsonPath("$.progressPercent").value(50.0));

        // Contribute more to complete it
        mockMvc.perform(post("/api/v1/finance/goals/{goalId}/contribute?amount=50000.00", goalId)
            .header("Authorization", "Bearer " + authToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void testGetMonthlyFinancialReport() throws Exception {
        var categories = categoryRepository.findByUserIdOrSystemDefaultTrue(userId);
        Long categoryId = categories.stream()
            .filter(c -> "Ăn uống".equals(c.getName()))
            .findFirst()
            .map(SpendingCategory::getId)
            .orElseThrow();

        // Add expenses
        BankingDTO.ExpenseRequest expenseReq1 = BankingDTO.ExpenseRequest.builder()
            .categoryId(categoryId)
            .amount(new BigDecimal("100.00"))
            .type(ExpenseType.EXPENSE)
            .expenseDate(LocalDate.of(2026, 4, 10))
            .build();

        BankingDTO.ExpenseRequest expenseReq2 = BankingDTO.ExpenseRequest.builder()
            .categoryId(categoryId)
            .amount(new BigDecimal("200.00"))
            .type(ExpenseType.INCOME)
            .expenseDate(LocalDate.of(2026, 4, 5))
            .build();

        mockMvc.perform(post("/api/v1/finance/expenses")
            .header("Authorization", "Bearer " + authToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(expenseReq1)))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/finance/expenses")
            .header("Authorization", "Bearer " + authToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(expenseReq2)))
            .andExpect(status().isCreated());

        // Get monthly report
        mockMvc.perform(get("/api/v1/finance/reports/monthly?year=2026&month=4")
            .header("Authorization", "Bearer " + authToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.year").value(2026))
            .andExpect(jsonPath("$.month").value(4))
            .andExpect(jsonPath("$.totalExpense").value(100.00))
            .andExpect(jsonPath("$.totalIncome").value(200.00))
            .andExpect(jsonPath("$.netBalance").value(100.00));
    }

    @Test
    void testUnauthorizedAccessDenied() throws Exception {
        mockMvc.perform(get("/api/v1/finance/categories"))
            .andExpect(status().isUnauthorized());
    }
}

