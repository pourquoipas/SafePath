package net.gnius.examples;

import net.gnius.safepath.SafePath;

import java.util.Optional;

// =================================================================================
// CLASSE DI ESEMPIO CON MAIN SafePathTester
// =================================================================================

public class SafePathTester {

    // --- Classi di Dati per il Test ---
    public static class Company {
        public String companyName = "ACME Corp";
    }

    public static class Address {
        private final String street;
        public Company company = new Company();
        public Address(String street) { this.street = street; }
        public String getStreet() { return street; }
        // FIXED: Added comma before the zip code.
        public String formatAddress(String city, String zip) { return street + ", " + city + ", " + zip; }
    }

    public static class User {
        private final Address address;
        public String name = "John Doe";
        public User(Address address) { this.address = address; }
        public Address getAddress() { return address; }
        public void throwError() { throw new IllegalStateException("This is an intentional error!"); }
    }

    // --- Main Method per l'Esecuzione dei Test ---
    public static void main(String[] args) {
        System.out.println("--- Running SafePath Demo in main() ---");

        Address goodAddress = new Address("123 Main St");
        User userWithGoodAddress = new User(goodAddress);
        User userWithNullAddress = new User(null);
        String defaultStreet = "Unknown Street";
        Address defaultAddressWithStreet = new Address("Street of Default Address");
        Address defaultAddressWithNullStreet = new Address(null);

        System.out.println("\n1. Test: Percorso sicuro completo");
        Optional<String> street = SafePath.invoke(userWithGoodAddress, "?.getAddress()?.getStreet()");
        System.out.println("   Risultato: " + street.orElse("FALLITO"));

        System.out.println("\n2. Test: Percorso sicuro, indirizzo nullo");
        Optional<String> nullStreet = SafePath.invoke(userWithNullAddress, "?.getAddress()?.getStreet()");
        System.out.println("   Risultato: " + nullStreet.orElse("Vuoto (corretto)"));

        System.out.println("\n3. Test: ?? per fornire un default (con #0)");
        Optional<String> streetWithDefault = SafePath.invoke(userWithNullAddress, "?.getAddress()?.getStreet() ?? #0", defaultStreet);
        System.out.println("   Risultato: " + streetWithDefault.orElse("FALLITO"));

        System.out.println("\n4. Test: Metodo con parametri (#0, #1)");
        Optional<String> formatted = SafePath.invoke(userWithGoodAddress, "?.getAddress()?.formatAddress(#0, #1)", "Springfield", "12345");
        System.out.println("   Risultato: " + formatted.orElse("FALLITO"));

        System.out.println("\n5. Test: Percorso non sicuro (.) su oggetto nullo");
        try {
            SafePath.invoke(userWithNullAddress, "?.getAddress().getStreet()");
        } catch (NullPointerException e) {
            System.out.println("   Risultato: Eccezione catturata (corretto) -> " + e.getMessage());
        }

        System.out.println("\n4. Test: Metodo con default su chiamate e parametri (#0, #1)");
        Optional<String> defaultOnCalls = SafePath.invoke(userWithNullAddress, "?.getAddress() ?? #2?.formatAddress(#0, #1).toUpperCase()", "Springfield", "12345", defaultAddressWithStreet);
        System.out.println("   Risultato: " + defaultOnCalls.orElse("FALLITO"));

        System.out.println("\n--- Demo Finished ---");
    }
}
