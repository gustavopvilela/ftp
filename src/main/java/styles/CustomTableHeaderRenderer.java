package styles;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import java.awt.*;

public class CustomTableHeaderRenderer extends DefaultTableCellRenderer {
    public CustomTableHeaderRenderer (JTable table) {
        JTableHeader header = table.getTableHeader();
        setOpaque(true);
        setForeground(new Color(40, 40, 40));
        setBackground(new Color(232, 236, 242));
        setHorizontalAlignment(CENTER);
        setFont(header.getFont().deriveFont(Font.BOLD));
        setBorder(UIManager.getBorder("TableHeader.cellBorder"));
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        return this;
    }
}
