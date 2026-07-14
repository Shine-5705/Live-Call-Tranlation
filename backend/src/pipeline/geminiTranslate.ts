import { GoogleGenerativeAI } from "@google/generative-ai";

const GEMINI_MODEL = process.env.GEMINI_MODEL ?? "gemini-2.5-flash";

export async function translateText(
  sourceText: string,
  sourceLang: string,
  targetLang: string
): Promise<string> {
  const apiKey = process.env.GEMINI_API_KEY;
  if (!apiKey) {
    console.warn("[translate] GEMINI_API_KEY not set, returning passthrough");
    return `[${targetLang}] ${sourceText}`;
  }

  const genAI = new GoogleGenerativeAI(apiKey);
  const model = genAI.getGenerativeModel({ model: GEMINI_MODEL });

  const prompt = `Translate the following text from ${sourceLang} to ${targetLang}.
Return ONLY the translated text. No explanations, no quotes, no commentary.
Preserve tone and register where possible.

Text: ${sourceText}`;

  const result = await model.generateContent(prompt);
  const text = result.response.text().trim();
  return text || sourceText;
}
