import java.util.Map;
import java.util.TreeMap;

/**
 * A tiny bank-ledger state machine: applies a sequence of deposit/withdraw ops to
 * accounts held in a TreeMap, throwing a USER-DEFINED OverdraftException (extends
 * RuntimeException) on insufficient funds — caught per-op and reported. Exercises a
 * custom throwable subclass (construction + throw + catch by declared type), sorted
 * map iteration, boxing, and string concat. Deterministic; byte-comparable.
 */
public class BankLedger {
    static class OverdraftException extends RuntimeException {
        OverdraftException(String m) {
            super(m);
        }
    }

    static final TreeMap accounts = new TreeMap();

    static int balance(String who) {
        Object v = accounts.get(who);
        return v == null ? 0 : ((Integer) v).intValue();
    }

    static void deposit(String who, int amt) {
        accounts.put(who, Integer.valueOf(balance(who) + amt));
    }

    static void withdraw(String who, int amt) {
        int b = balance(who);
        if (amt > b) {
            throw new OverdraftException(who + " overdraft: balance " + b + " < withdraw " + amt);
        }
        accounts.put(who, Integer.valueOf(b - amt));
    }

    public static void main(String[] args) {
        String[][] ops = {
            { "deposit", "alice", "100" },
            { "deposit", "bob", "50" },
            { "withdraw", "alice", "30" },
            { "withdraw", "bob", "80" },
            { "deposit", "carol", "200" },
            { "withdraw", "carol", "250" },
            { "withdraw", "alice", "20" },
            { "deposit", "bob", "25" },
            { "withdraw", "dave", "10" },
        };
        for (String[] op : ops) {
            String kind = op[0];
            String who = op[1];
            int amt = Integer.parseInt(op[2]);
            try {
                if (kind.equals("deposit")) {
                    deposit(who, amt);
                } else {
                    withdraw(who, amt);
                }
                System.out.println("OK     " + kind + " " + who + " " + amt);
            } catch (OverdraftException e) {
                System.out.println("DENIED " + e.getMessage());
            }
        }
        System.out.println("--- balances ---");
        int total = 0;
        for (Object e : accounts.entrySet()) {
            Map.Entry en = (Map.Entry) e;
            int b = ((Integer) en.getValue()).intValue();
            total += b;
            System.out.println(en.getKey() + " = " + b);
        }
        System.out.println("total = " + total);
    }
}
