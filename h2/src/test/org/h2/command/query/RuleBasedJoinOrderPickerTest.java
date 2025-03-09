package org.h2.command.query;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.h2.engine.SessionLocal;
import org.h2.table.Table;
import org.h2.table.TableFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;

public class RuleBasedJoinOrderPickerTest {
  SessionLocal mockSession;

  RuleBasedJoinOrderPicker ruleBasedJoinOrderPicker;

  @BeforeEach
  public void setUp(){
    mockSession = Mockito.mock(SessionLocal.class);
    Mockito.when(mockSession.nextObjectId()).thenReturn(1);
  }

  @Test
  public void bestOrder_singleTable(){
    Table customers = Mockito.mock(Table.class);

    TableFilter tableFilter = new TableFilter(mockSession, customers, "customers", true, null, 0, null);
    TableFilter[] filters = {tableFilter};

    ruleBasedJoinOrderPicker = new RuleBasedJoinOrderPicker(mockSession, filters);

    assertEquals(filters, ruleBasedJoinOrderPicker.bestOrder());
  }
}
