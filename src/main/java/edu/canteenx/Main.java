package edu.canteenx;

import edu.canteenx.ui.CanteenXFrame;
import javax.swing.*;

public final class Main {
    private Main() {}
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
            catch (Exception ignored) { }
            new CanteenXFrame().setVisible(true);
        });
    }
}
