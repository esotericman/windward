/*
 * Copyright (C) 2023 Flmelody.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.flmelody.core.plugin.resource;

import java.util.function.Consumer;
import org.flmelody.core.context.WindwardContext;

/**
 * @author esotericman
 */
public class BaseStaticResourcePlugin implements ResourcePlugin, Consumer<WindwardContext> {
  protected final String[] staticResourceLocations;

  public BaseStaticResourcePlugin(String[] staticResourceLocations) {
    this.staticResourceLocations = staticResourceLocations;
  }

  @Override
  public void accept(WindwardContext windwardContext) {
    // todo
  }
}
