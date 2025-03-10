package org.h2.command.query;

import org.h2.engine.SessionLocal;
import org.h2.expression.Expression;
import org.h2.table.TableFilter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class RuleBasedJoinOrderPicker {
  final SessionLocal session;
  final TableFilter[] filters;

  final Map<String, Set<String>> joinMap = new HashMap<>();

  public RuleBasedJoinOrderPicker(SessionLocal session, TableFilter[] filters) {
    this.session = session;
    this.filters = filters;

    // for every join pair, add mapping in both directions to our joinMap
    identifyJoinPairs().stream().forEach(joinPair -> {
      joinMap.computeIfAbsent(joinPair.left, k -> new HashSet<>()).add(joinPair.right);
      joinMap.computeIfAbsent(joinPair.right, k -> new HashSet<>()).add(joinPair.left);
    });
  }

  long getRowCount(TableFilter filter){
    long rowCountApprox = filter.getTable().getRowCountApproximation(session);
    return rowCountApprox;
  }

  boolean canAddToCurrentSet(List<TableFilter> currentTables, TableFilter potentialNext) {
    if (currentTables.isEmpty()) {
      return true;
    }

    Set<String> tablesAlreadyInJoin = new HashSet<>();
    currentTables.stream().forEach(filter -> tablesAlreadyInJoin.add(filter.getTable().getName()));

    Set<String> tablesEligibleForAdding = tablesAlreadyInJoin.stream().flatMap(table ->
          joinMap.get(table).stream()).collect(Collectors.toSet());

    return tablesEligibleForAdding.contains(potentialNext.getTable().getName());
  }

  public TableFilter[] bestOrder(){
    List<TableFilter> resultingOrder = new ArrayList<>();
    List<TableFilter> filtersList = new ArrayList<>(Arrays.asList(filters));

    while (!filtersList.isEmpty()){
      List<TableFilter> validCandidates = filtersList.stream().filter(
          table -> canAddToCurrentSet(resultingOrder, table)).collect(Collectors.toList());

      TableFilter fewestRows = validCandidates.stream().min(Comparator.comparingLong(filter -> getRowCount(filter))).get();
      resultingOrder.add(fewestRows);
      filtersList.remove(fewestRows);
    }

    return resultingOrder.toArray(new TableFilter[0]);
  }

  String getTableNameOrAlias(Expression expression){
    if (expression.getTableName() != null) {
      return expression.getTableName();
    }

    return expression.getTableAlias();
  }

  List<JoinPair> identifyJoinPairs(){
    if (filters[0].getFullCondition() == null){
      return new ArrayList<>();
    }

    List<JoinPair> results = new ArrayList<>();

    List<Expression> expressionsToConsider = new ArrayList<>();
    expressionsToConsider.add(filters[0].getFullCondition());

    while (!expressionsToConsider.isEmpty()){
      Expression current = expressionsToConsider.remove(0);
      // looking for expressions that have a left and right leaf expression each involving a different table

      if (current.getSubexpressionCount() == 2 &&
          current.getSubexpression(0).getSubexpressionCount() == 0 &&
          current.getSubexpression(1).getSubexpressionCount() == 0)
      {
        String leftTable = getTableNameOrAlias(current.getSubexpression(0));
        String rightTable = getTableNameOrAlias(current.getSubexpression(1));

        if (leftTable != null && rightTable != null && !leftTable.equals(rightTable)) {
          System.out.println("Found join expression for: " + leftTable + " and " + rightTable);

          results.add(new JoinPair(leftTable, rightTable));
          continue;
        }
      }

      for (int sub = 0; sub < current.getSubexpressionCount(); sub++){
        expressionsToConsider.add(current.getSubexpression(sub));
      }
    }

    return results;
  }
}

final class JoinPair{
  final String left;
  final String right;

  public JoinPair(String left, String right) {
    this.left = left;
    this.right = right;
  }

  boolean matchesEither(String table){
    return left.equals(table) || right.equals(table);
  }

  String other(String table){
    if (left.equals(table)) {
      return right;
    }

    if (right.equals(table)) {
      return left;
    }

    return null;
  }
}
