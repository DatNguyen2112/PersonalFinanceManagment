package BankingSystem.Exception;

public class SepayApiExecption extends RuntimeException {
    public SepayApiExecption(String message) { super(message); }
    public SepayApiExecption(String message, Throwable cause) { super(message, cause); }
}