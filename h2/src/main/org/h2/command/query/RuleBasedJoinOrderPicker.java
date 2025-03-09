package org.h2.command.query;

import org.h2.engine.SessionLocal;
import org.h2.table.TableFilter;

public class RuleBasedJoinOrderPicker {
  final SessionLocal session;
  final TableFilter[] filters;

  public RuleBasedJoinOrderPicker(SessionLocal session, TableFilter[] filters) {
    this.session = session;
    this.filters = filters;
  }

  public TableFilter[] bestOrder(){
    return filters;
  }
}
