package org.flmelody.core;

import org.flmelody.core.context.WindwardContext;

/**
 * @author esotericman
 */
public interface Handler {
  void invoke(WindwardContext windwardContext);
}
