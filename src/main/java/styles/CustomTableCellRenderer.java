package styles;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;

public class CustomTableCellRenderer extends DefaultTableCellRenderer {
    private static final int PADDING = 10;

    public CustomTableCellRenderer () {
        super();
    }

    @Override
    public Component getTableCellRendererComponent (JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

        if (c instanceof JLabel) {
            JLabel label = (JLabel) c;
            label.setBorder(BorderFactory.createEmptyBorder(0, PADDING, 0, PADDING));

            if (column == 0 || column == 2) {
                label.setHorizontalAlignment(SwingConstants.CENTER);
            }
            else {
                label.setHorizontalAlignment(SwingConstants.LEFT);
            }
        }

        if (!isSelected) {
            c.setBackground(row % 2 == 0 ? Color.WHITE : new Color(248, 250, 252));
        }

        return c;
    }
}
