public class TestHex {
    public static void main(String[] args) {
        try {
            String h = "0x123";
            System.out.println(Double.parseDouble(h + "p0"));
            String h2 = "0X2C";
            System.out.println(Double.parseDouble(h2 + "p0"));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
