package com.company.s3explorer.ui.repository;

import com.company.s3explorer.repository.RepositoryDefinition;

import javax.swing.table.AbstractTableModel;
import java.text.Collator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class RepositoryTableModel extends AbstractTableModel {
    private final List<RepositoryDefinition> repositories = new ArrayList<>();

    private static final String[] COLUMNS = {
            "Name",
            "Endpoint",
            "Access Key"
    };

    @Override
    public int getRowCount() {
        return repositories.size();
    }

    @Override
    public int getColumnCount() {
        return COLUMNS.length;
    }

    @Override
    public String getColumnName(int column) {
        return COLUMNS[column];
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        RepositoryDefinition repo = repositories.get(rowIndex);

        return switch (columnIndex) {
            case 0 -> repo.getName();
            case 1 -> repo.getEndpoint();
            case 2 -> repo.getAccessKey();
            default -> "";
        };
    }

    public void setRepositories(
            List<RepositoryDefinition> list) {

        repositories.clear();

        if (list != null) {
            repositories.addAll(list);
        }

        Collator collator =
                Collator.getInstance(
                        new Locale("tr", "TR"));

        collator.setStrength(
                Collator.PRIMARY);

        repositories.sort(
                (left, right) ->
                        collator.compare(
                                left.getName(),
                                right.getName()));

        fireTableDataChanged();
    }
    
    public RepositoryDefinition getRepository(int row) {
        return repositories.get(row);
    }
}