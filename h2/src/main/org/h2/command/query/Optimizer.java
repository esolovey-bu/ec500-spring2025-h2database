/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.command.query;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import org.h2.engine.SessionLocal;
import org.h2.expression.Expression;
import org.h2.table.Plan;
import org.h2.table.PlanItem;
import org.h2.table.Table;
import org.h2.table.TableFilter;
import org.h2.util.Permutations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The optimizer is responsible to find the best execution plan
 * for a given query.
 */
class Optimizer {
    private static final int MAX_BRUTE_FORCE_FILTERS = 7;
    private static final int MAX_BRUTE_FORCE = 2000;
    private static final int MAX_GENETIC = 500;
    private long startNs;
    private BitSet switched;

    //  possible plans for filters, if using brute force:
    //  1 filter 1 plan
    //  2 filters 2 plans
    //  3 filters 6 plans
    //  4 filters 24 plans
    //  5 filters 120 plans
    //  6 filters 720 plans
    //  7 filters 5040 plans
    //  8 filters 40320 plan
    //  9 filters 362880 plans
    // 10 filters 3628800 filters

    private final TableFilter[] filters;
    private final Expression condition;
    private final SessionLocal session;

    private Plan bestPlan;
    private TableFilter topFilter;
    private double cost;
    private Random random;
    private final AllColumnsForPlan allColumnsSet;

    Optimizer(TableFilter[] filters, Expression condition, SessionLocal session) {
        this.filters = filters;
        this.condition = condition;
        this.session = session;
        allColumnsSet = new AllColumnsForPlan(filters);
    }

    /**
     * How many filter to calculate using brute force. The remaining filters are
     * selected using a greedy algorithm which has a runtime of (1 + 2 + ... +
     * n) = (n * (n-1) / 2) for n filters. The brute force algorithm has a
     * runtime of n * (n-1) * ... * (n-m) when calculating m brute force of n
     * total. The combined runtime is (brute force) * (greedy).
     *
     * @param filterCount the number of filters total
     * @return the number of filters to calculate using brute force
     */
    private static int getMaxBruteForceFilters(int filterCount) {
        int i = 0, j = filterCount, total = filterCount;
        while (j > 0 && total * (j * (j - 1) / 2) < MAX_BRUTE_FORCE) {
            j--;
            total *= j;
            i++;
        }
        return i;
    }

    private void calculateBestPlan(boolean isSelectCommand) {
        cost = -1;
        if (filters.length == 1) {
            testPlan(filters, isSelectCommand);
        } else {
            startNs = System.nanoTime();
            if (filters.length <= MAX_BRUTE_FORCE_FILTERS) {
//                calculateBruteForceAll(isSelectCommand);
                calculateRuleBasedPlan(isSelectCommand);
            } else {
                calculateBruteForceSome(isSelectCommand);
                random = new Random(0);
                calculateGenetic(isSelectCommand);
            }
        }
    }

    private void calculateFakePlan() {
        cost = -1;
        bestPlan = new Plan(filters, filters.length, condition);
    }

    private boolean canStop(int x) {
        return (x & 127) == 0
                // don't calculate for simple queries (no rows or so)
                && cost >= 0
                // 100 microseconds * cost
                && System.nanoTime() - startNs > cost * 100_000L;
    }

    private void calculateBruteForceAll(boolean isSelectCommand) {
        TableFilter[] list = new TableFilter[filters.length];
        Permutations<TableFilter> p = Permutations.create(filters, list);

        p.next();
        testPlan(list, isSelectCommand);

//        for (int x = 0; !canStop(x) && p.next(); x++) {
//            testPlan(list, isSelectCommand);
//        }
    }

    void calculateRuleBasedPlan(boolean isSelectCommand){
        List<TableFilter> resultingOrder = new ArrayList<>();

        List<TableFilter> filtersList = new ArrayList<>(Arrays.asList(filters));
        for (TableFilter filter : filtersList){
            Table table = filter.getTable();
            long rowCount = table.getRowCount(session);

            System.out.println(table.getName() + " row count: " + rowCount);
        }

        while (!filtersList.isEmpty()){
            List<TableFilter> validCandidates = filtersList.stream().filter(
                table -> canAddToCurrentSet(resultingOrder, table)).collect(Collectors.toList());

            System.out.println("valid candidates: " + validCandidates);

            TableFilter fewestRows = validCandidates.stream().min(Comparator.comparingLong(filter -> getRowCount(filter))).get();
            resultingOrder.add(fewestRows);
            filtersList.remove(fewestRows);
        }

        System.out.println("Result: " + resultingOrder);
        TableFilter[] resultArray = resultingOrder.toArray(new TableFilter[0]);
        testPlan(resultArray, isSelectCommand);
    }

    long getRowCount(TableFilter filter){
        return filter.getTable().getRowCount(session);
    }

    String getTableNameOrAlias(Expression expression){
        if (expression.getTableName() != null) {
            return expression.getTableName();
        }

        return expression.getTableAlias();
    }

    boolean canAddToCurrentSet(List<TableFilter> currentTables, TableFilter potentialNext) {
        if (currentTables.isEmpty()) {
            return true;
        }

        List<Expression> expressionsToConsider = new ArrayList<>();
        expressionsToConsider.add(potentialNext.getFullCondition());

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
                    System.out.println("Found join expression: " + current);
                    continue;
                }
            }

            for (int sub = 0; sub < current.getSubexpressionCount(); sub++){
                expressionsToConsider.add(current.getSubexpression(sub));
            }
        }


        // at least one of the tables in the current set must have a join expression against table under consideration
        return false;
    }

    private void calculateBruteForceSome(boolean isSelectCommand) {
        int bruteForce = getMaxBruteForceFilters(filters.length);
        TableFilter[] list = new TableFilter[filters.length];
        Permutations<TableFilter> p = Permutations.create(filters, list, bruteForce);
        for (int x = 0; !canStop(x) && p.next(); x++) {
            // find out what filters are not used yet
            for (TableFilter f : filters) {
                f.setUsed(false);
            }
            for (int i = 0; i < bruteForce; i++) {
                list[i].setUsed(true);
            }
            // fill the remaining elements with the unused elements (greedy)
            for (int i = bruteForce; i < filters.length; i++) {
                double costPart = -1.0;
                int bestPart = -1;
                for (int j = 0; j < filters.length; j++) {
                    if (!filters[j].isUsed()) {
                        if (i == filters.length - 1) {
                            bestPart = j;
                            break;
                        }
                        list[i] = filters[j];
                        Plan part = new Plan(list, i+1, condition);
                        double costNow = part.calculateCost(session, allColumnsSet, isSelectCommand);
                        if (costPart < 0 || costNow < costPart) {
                            costPart = costNow;
                            bestPart = j;
                        }
                    }
                }
                filters[bestPart].setUsed(true);
                list[i] = filters[bestPart];
            }
            testPlan(list, isSelectCommand);
        }
    }

    private void calculateGenetic(boolean isSelectCommand) {
        TableFilter[] best = new TableFilter[filters.length];
        TableFilter[] list = new TableFilter[filters.length];
        for (int x = 0; x < MAX_GENETIC; x++) {
            if (canStop(x)) {
                break;
            }
            boolean generateRandom = (x & 127) == 0;
            if (!generateRandom) {
                System.arraycopy(best, 0, list, 0, filters.length);
                if (!shuffleTwo(list)) {
                    generateRandom = true;
                }
            }
            if (generateRandom) {
                switched = new BitSet();
                System.arraycopy(filters, 0, best, 0, filters.length);
                shuffleAll(best);
                System.arraycopy(best, 0, list, 0, filters.length);
            }
            if (testPlan(list, isSelectCommand)) {
                switched = new BitSet();
                System.arraycopy(list, 0, best, 0, filters.length);
            }
        }
    }

    private boolean testPlan(TableFilter[] list, boolean isSelectCommand) {
        Plan p = new Plan(list, list.length, condition);
        double costNow = p.calculateCost(session, allColumnsSet, isSelectCommand);
        if (cost < 0 || costNow < cost) {
            cost = costNow;
            bestPlan = p;
            return true;
        }
        return false;
    }

    private void shuffleAll(TableFilter[] f) {
        for (int i = 0; i < f.length - 1; i++) {
            int j = i + random.nextInt(f.length - i);
            if (j != i) {
                TableFilter temp = f[i];
                f[i] = f[j];
                f[j] = temp;
            }
        }
    }

    private boolean shuffleTwo(TableFilter[] f) {
        int a = 0, b = 0, i = 0;
        for (; i < 20; i++) {
            a = random.nextInt(f.length);
            b = random.nextInt(f.length);
            if (a == b) {
                continue;
            }
            if (a < b) {
                int temp = a;
                a = b;
                b = temp;
            }
            int s = a * f.length + b;
            if (switched.get(s)) {
                continue;
            }
            switched.set(s);
            break;
        }
        if (i == 20) {
            return false;
        }
        TableFilter temp = f[a];
        f[a] = f[b];
        f[b] = temp;
        return true;
    }

    /**
     * Calculate the best query plan to use.
     *
     * @param parse If we do not need to really get the best plan because it is
     *            a view parsing stage.
     */
    void optimize(boolean parse, boolean isSelectCommand) {
        if (parse) {
            calculateFakePlan();
        } else {
            calculateBestPlan(isSelectCommand);
            bestPlan.removeUnusableIndexConditions();
        }
        TableFilter[] f2 = bestPlan.getFilters();
        topFilter = f2[0];
        for (int i = 0; i < f2.length - 1; i++) {
            f2[i].addJoin(f2[i + 1], false, null);
        }
        if (parse) {
            return;
        }
        for (TableFilter f : f2) {
            PlanItem item = bestPlan.getItem(f);
            f.setPlanItem(item);
        }
    }

    public TableFilter getTopFilter() {
        return topFilter;
    }

    double getCost() {
        return cost;
    }

}
