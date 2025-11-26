package java;

/**
 * A
 */
public final class MainRBH {

    /**
     * A
     */
    public MainRBH() {
    }

    //static final ReadBiasedHolder.ReadBiasedHolderImpl<Integer> HOLDER = ReadBiasedHolder.of(Integer.class);
    static final ReadBiasedHolder2<Integer> HOLDER = ReadBiasedHolder2.of(Integer.class);

    void main() throws InterruptedException {
        HOLDER.set(1);
        int sum = 0;
        for (int i = 0; i < 1_000_000; i++) {
            sum += payload();
        }
        IO.println(sum);
        Thread.sleep(1_000);
        IO.println("Done");
    }

    int payload() {
        return HOLDER.get();
    }

}
