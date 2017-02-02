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
package org.jetbrains.kotlin.hints.intentions

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.util.PsiUtilCore
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.constants.evaluate.ConstantExpressionEvaluator
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.diagnostics.netbeans.parser.KotlinParserResult
import org.jetbrains.kotlin.reformatting.format
import org.jetbrains.kotlin.hints.atomicChange

class ConvertToStringTemplateIntention(val parserResult: KotlinParserResult,
                                       val psi: PsiElement) : ApplicableIntention {
    private var expression: KtBinaryExpression? = null

    override fun isApplicable(caretOffset: Int): Boolean {
        expression = psi.getNonStrictParentOfType(KtBinaryExpression::class.java) ?: return false
        val element = expression ?: return false

        if (!isApplicableToNoParentCheck(element)) return false

        val parent = element.parent
        if (parent is KtBinaryExpression && isApplicableToNoParentCheck(parent)) return false

        return true    
    }

    override fun getDescription() = "Convert concatenation to template"

    override fun implement() {
        val element = expression ?: return
        
        val text = buildReplacement(element).text
        
        val doc = parserResult.snapshot.source.getDocument(false)
        
        val startOffset = element.textRange.startOffset
        val lengthToDelete = element.textLength
        
        doc.atomicChange {
            remove(startOffset, lengthToDelete)
            insertString(startOffset, text, null)
            format(this, psi.textRange.startOffset)
        }
    }


    // copied from IDEA plugin
    
    companion object {
        fun shouldSuggestToConvert(expression: KtBinaryExpression): Boolean {
            val entries = buildReplacement(expression).entries
            return entries.none { it is KtBlockStringTemplateEntry }
                    && !entries.all { it is KtLiteralStringTemplateEntry || it is KtEscapeStringTemplateEntry }
                    && entries.count { it is KtLiteralStringTemplateEntry } > 1
                    && !expression.textContains('\n')
        }

        private fun buildReplacement(expression: KtBinaryExpression): KtStringTemplateExpression {
            val rightText = buildText(expression.right, false)
            return fold(expression.left, rightText, KtPsiFactory(expression))
        }

        private fun fold(left: KtExpression?, right: String, factory: KtPsiFactory): KtStringTemplateExpression {
            val forceBraces = !right.isEmpty() && right.first() != '$' && right.first().isJavaIdentifierPart()

            if (left is KtBinaryExpression && isApplicableToNoParentCheck(left)) {
                val leftRight = buildText(left.right, forceBraces)
                return fold(left.left, leftRight + right, factory)
            } else {
                val leftText = buildText(left, forceBraces)
                return factory.createExpression("\"$leftText$right\"") as KtStringTemplateExpression
            }
        }

        private fun buildText(expr: KtExpression?, forceBraces: Boolean): String {
            if (expr == null) return ""
            val expression = KtPsiUtil.safeDeparenthesize(expr)
            val expressionText = expression.text
            when (expression) {
                is KtConstantExpression -> {
                    val bindingContext = expression.analyze(BodyResolveMode.PARTIAL)
                    val type = bindingContext.getType(expression)!!

                    if (KotlinBuiltIns.isChar(type)) {
                        val value = expressionText.removePrefix("'").removeSuffix("'")
                        return when (value) { // escape double quote and unescape single one
                            "\"" -> "\\\""
                            "\\'" -> "'"
                            else -> value
                        }
                    }

                    val constant = ConstantExpressionEvaluator.getConstant(expression, bindingContext)
                    val stringValue = constant?.getValue(type).toString()
                    if (stringValue == expressionText) {
                        return StringUtil.escapeStringCharacters(stringValue)
                    }
                }

                is KtStringTemplateExpression -> {
                    val base = if (expressionText.startsWith("\"\"\"") && expressionText.endsWith("\"\"\"")) {
                        val unquoted = expressionText.substring(3, expressionText.length - 3)
                        StringUtil.escapeStringCharacters(unquoted)
                    } else {
                        StringUtil.unquoteString(expressionText)
                    }

                    if (forceBraces) {
                        if (base.endsWith('$')) {
                            return base.dropLast(1) + "\\$"
                        } else {
                            val lastPart = expression.children.lastOrNull()
                            if (lastPart is KtSimpleNameStringTemplateEntry) {
                                return base.dropLast(lastPart.textLength) + "\${" + lastPart.text.drop(1) + "}"
                            }
                        }
                    }
                    return base
                }

                is KtNameReferenceExpression ->
                    return "$" + (if (forceBraces) "{$expressionText}" else expressionText)
            }

            return "\${$expressionText}"
        }

        private fun isApplicableToNoParentCheck(expression: KtBinaryExpression): Boolean {
            if (expression.operationToken != KtTokens.PLUS) return false
            val expressionType = expression.analyze(BodyResolveMode.PARTIAL).getType(expression)
            if (!KotlinBuiltIns.isString(expressionType)) return false
            return isSuitable(expression)
        }

        private fun isSuitable(expression: KtExpression): Boolean {
            if (expression is KtBinaryExpression && expression.operationToken == KtTokens.PLUS) {
                return isSuitable(expression.left ?: return false) && isSuitable(expression.right ?: return false)
            }

            if (PsiUtilCore.hasErrorElementChild(expression)) return false
            if (expression.textContains('\n')) return false
            return true
        }
    }

}