/*******************************************************************************
 * Copyright 2000-2016 JetBrains s.r.o.
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
 *
 *******************************************************************************/
package org.jetbrains.kotlin.file.wizard.classwizard;

import org.jetbrains.kotlin.file.wizard.KtDefaultWizardIterator;
import org.netbeans.api.templates.TemplateRegistration;
import org.openide.util.NbBundle.Messages;

@TemplateRegistration(folder = "Kotlin", 
        displayName = "Kotlin class", 
        content = "class.kt",
        iconBase = "org/jetbrains/kotlin/kt.png", 
        description = "ktClass.html",
        scriptEngine="freemarker")
@Messages("KtClassWizardIterator_displayName=Kotlin class")
public final class KtClassWizardIterator extends KtDefaultWizardIterator {
    public KtClassWizardIterator() {
        super("Class");
    }
}
