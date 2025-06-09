package net.gnius.safepath;

// =================================================================================
// CLASSE DI TEST JUNIT 5 SafePathJunitTest
// =================================================================================

import net.gnius.examples.SafePathTester;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SafePathJunitTest {

    private SafePathTester.User userWithGoodAddress;
    private SafePathTester.User userWithNullAddress;
    private final String defaultStreet = "Unknown Street";

    @BeforeEach
    void setUp() {
        SafePathTester.Address goodAddress = new SafePathTester.Address("123 Main St");
        userWithGoodAddress = new SafePathTester.User(goodAddress);
        userWithNullAddress = new SafePathTester.User(null);
    }

    @Test
    @DisplayName("Should return value on a full safe path")
    void testSuccessfulSafePath() {
        Optional<String> result = SafePath.invoke(userWithGoodAddress, "?.getAddress()?.getStreet()");
        assertTrue(result.isPresent(), "Result should not be empty");
        assertEquals("123 Main St", result.get(), "Should return the correct street");
    }

    @Test
    @DisplayName("Should return empty Optional when an intermediate object is null")
    void testSafePathWithNullIntermediate() {
        Optional<String> result = SafePath.invoke(userWithNullAddress, "?.getAddress()?.getStreet()");
        assertFalse(result.isPresent(), "Result should be empty for a null path");
    }

    @Test
    @DisplayName("Should return the default value using ?? #0 operator")
    void testNullCoalescingOperator() {
        Optional<String> result = SafePath.invoke(userWithNullAddress, "?.getAddress()?.getStreet() ?? #0", defaultStreet);
        assertTrue(result.isPresent(), "Default value should be present");
        assertEquals(defaultStreet, result.get(), "Should return the default street");
    }

    @Test
    @DisplayName("Should correctly invoke a method with positional parameters #0 and #1")
    void testMethodWithParameters() {
        Optional<String> result = SafePath.invoke(userWithGoodAddress, "?.getAddress()?.formatAddress(#0, #1)", "Springfield", "12345");
        assertTrue(result.isPresent());
        assertEquals("123 Main St, Springfield, 12345", result.get());
    }

    @Test
    @DisplayName("Should access a public field directly")
    void testPublicFieldAccess() {
        Optional<String> result = SafePath.invoke(userWithGoodAddress, "?.name");
        assertTrue(result.isPresent());
        assertEquals("John Doe", result.get());
    }

    @Test
    @DisplayName("Should throw NullPointerException for unsafe access on null")
    void testUnsafeAccessThrowsNPE() {
        NullPointerException exception = assertThrows(NullPointerException.class, () -> {
            SafePath.invoke(null, ".name");
        });
        assertTrue(exception.getMessage().contains("'root' is null"));
    }

    @Test
    @DisplayName("Should throw NullPointerException for unsafe access on intermediate null")
    void testUnsafeAccessOnIntermediateNullThrowsNPE() {
        NullPointerException exception = assertThrows(NullPointerException.class, () -> {
            SafePath.invoke(userWithNullAddress, "?.getAddress().getStreet()");
        });
        assertTrue(exception.getMessage().contains("'getAddress' is null"));
    }

    @Test
    @DisplayName("Should rethrow the exception from an invoked method")
    void testRethrowsInvokedMethodException() {
        // Here we expect SafePath to fail because the underlying method throwError() fails.
        // The exception from the method should be the direct cause of the SafePathException.
        SafePath.SafePathException exception = assertThrows(SafePath.SafePathException.class, () -> {
            SafePath.invoke(userWithGoodAddress, "?.throwError()");
        });
        // FIXED: The direct cause is the IllegalStateException, not its cause.
        assertInstanceOf(IllegalStateException.class, exception.getCause());
        assertEquals("This is an intentional error!", exception.getCause().getMessage());
    }

    @Test
    @DisplayName("Should throw exception for non-existent method")
    void testNonExistentMethod() {
        SafePath.SafePathException exception = assertThrows(SafePath.SafePathException.class, () -> {
            SafePath.invoke(userWithGoodAddress, "?.getAddress()?.getCity()");
        });
        assertInstanceOf(NoSuchMethodException.class, exception.getCause());
    }

    @Test
    @DisplayName("Should throw NPE on complex path with unsafe access after safe null")
    void testComplexPathWithUnsafeAccessThrowsNPE() {
        // FIXED: This test now correctly expects an NPE and checks the message.
        NullPointerException exception = assertThrows(NullPointerException.class, () -> {
            // ?.getAddress() is null.
            // ?.company on null is null.
            // .companyName on null should throw.
            SafePath.invoke(userWithNullAddress, "?.getAddress()?.company.companyName ?? #0", "Default Company");
        });
        // The last successful operation that returned a value was getting the User object.
        // The operation that resulted in null was 'getAddress'.
        // The next safe op 'company' was on a null object.
        // The unsafe op 'companyName' is on a null object, and the last op was 'company'.
        assertTrue(exception.getMessage().contains("'company' is null"));
    }

    @Test
    @DisplayName("Should reuse positional parameters")
    void testParameterReuse() {
        Optional<String> result = SafePath.invoke(userWithGoodAddress, "?.getAddress().formatAddress(#0, #0)", "TestCity");
        assertTrue(result.isPresent());
        assertEquals("123 Main St, TestCity, TestCity", result.get());
    }

    @Test
    @DisplayName("Should handle default values in the middle of the path")
    void testDefaultInMiddleOfPath() {
        // First case: default address is not null
        Optional<String> result = SafePath.invoke(userWithNullAddress, "?.getAddress() ?? #0?.getStreet() ?? #1", new SafePathTester.Address("Default Address"), "Default Street");
        assertTrue(result.isPresent());
        assertEquals("Default Address", result.get());

        // Second case: default address is null
        result = SafePath.invoke(userWithNullAddress, "?.getAddress() ?? #0?.getStreet() ?? #1", null, "Default Street");
        assertTrue(result.isPresent());
        assertEquals("Default Street", result.get());
    }

}
