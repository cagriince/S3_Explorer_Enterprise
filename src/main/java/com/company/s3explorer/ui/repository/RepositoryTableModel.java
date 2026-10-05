package com.company.s3explorer.ui.repository;

import com.company.s3explorer.repository.RepositoryDefinition;
import com.company.s3explorer.util.S3Util;

import javax.swing.table.AbstractTableModel;
import java.text.Collator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class RepositoryTableModel
        extends AbstractTableModel {

    private final List<RepositoryDefinition> repositories =
            new ArrayList<>();

    private static final String[] COLUMNS = {
            "Name",
            "Environment",
            "Endpoint",
            "Access Key",
            "External Bucket",
            "Encryption"
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
    public String getColumnName(
            int column) {

        return COLUMNS[column];
    }

    @Override
    public Object getValueAt(
            int rowIndex,
            int columnIndex) {

        RepositoryDefinition repo =
                repositories.get(rowIndex);

        return switch (columnIndex) {

            case 0 ->
                    repo.getName();

            case 1 ->
                    repo.getEnvironment();

            case 2 ->
                    repo.getEndpoint();

            case 3 ->
                    repo.getAccessKey();

            case 4 ->
                    repo.hasExternalBucket();

            case 5 ->
                    repo.hasEncryptionConfiguration();

            default ->
                    "";
        };
    }

    @Override
    public Class<?> getColumnClass(
            int columnIndex) {

        if (columnIndex == 4
                || columnIndex == 5) {

            return Boolean.class;
        }

        return super.getColumnClass(
                columnIndex);
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

        repositories.sort((left, right) ->
                S3Util.naturalTurkishCompare(
                        left.getId(),
                        right.getId(),
                        collator));
/*        repositories.sort(
                (left, right) ->
                        collator.compare(
                                left.getId(),
                                right.getId()));
*/
        fireTableDataChanged();
    }

    public RepositoryDefinition getRepository(
            int row) {

        return repositories.get(row);
    }
}