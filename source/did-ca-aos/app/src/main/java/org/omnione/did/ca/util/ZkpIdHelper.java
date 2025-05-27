/*
 * Copyright 2024-2025 OmniOne.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.omnione.did.ca.util;

public class ZkpIdHelper {
    private static String DELIMITER = ":";

    public static String extractSchemaIdFromDefinitionId(String definitionId) {
        String[] parts = definitionId.split(DELIMITER);
        if (parts.length < 8) {
            throw new IllegalArgumentException("Invalid credential definition ID format.");
        }
        return String.join(DELIMITER, parts[3], parts[4], parts[5], parts[6]);
    }
}
