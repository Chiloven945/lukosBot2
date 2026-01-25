//void main() {
//    String[] props = {
//            "java.version",            // OpenJDK version (e.g., 25.0.1)
//            "java.runtime.version",    // Full runtime build (e.g., 25.0.1+8-LTS)
//            "java.runtime.name",       // Brand name (e.g., OpenJDK Runtime Environment)
//            "java.vendor",             // Entity (e.g., Azul Systems, Inc.)
//            "java.vm.name",            // VM implementation & Build ID (e.g., Zulu25.30+17-CA)
//            "java.vm.version",         // Often where the specific Zulu version hides
//            "java.vm.vendor",          // VM vendor
//            "java.specification.version" // Platform spec (e.g., 25)
//    };
//
//    for (String prop : props) {
//        try {
//            String value = System.getProperty(prop);
//            IO.printf("%-25s : %s%n", prop, value != null ? value : "null");
//        } catch (Exception _) {
//            IO.println("Could not access: " + prop);
//        }
//    }
//}

void main() {
    new TreeMap<>(System.getProperties()).forEach(
            (key, value) -> IO.println(key + " = " + value)
    );
}

