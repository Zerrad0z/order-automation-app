import i18n from "i18next";
import { initReactI18next } from "react-i18next";
import en from "./locales/en/common.json";
import fr from "./locales/fr/common.json";

void i18n.use(initReactI18next).init({
  resources: { en: { translation: en }, fr: { translation: fr } },
  fallbackLng: "en",
  lng: "en",
  returnEmptyString: false,
  interpolation: { escapeValue: false },
});

export default i18n;
