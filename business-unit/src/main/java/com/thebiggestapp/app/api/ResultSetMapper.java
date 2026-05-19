package com.thebiggestapp.app.api;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ResultSetMapper {

	public static List<Map<String, Object>> toList(ResultSet rs) throws SQLException {
		List<Map<String, Object>> list = new ArrayList<>();
		ResultSetMetaData metaData = rs.getMetaData();
		int columnCount = metaData.getColumnCount();

		while (rs.next()) {
			Map<String, Object> row = new HashMap<>();
			for (int i = 1; i <= columnCount; i++) {
				String columnName = metaData.getColumnName(i);
				Object columnValue = rs.getObject(i);
				row.put(columnName, columnValue);
			}
			list.add(row);
		}

		return list;
	}
}