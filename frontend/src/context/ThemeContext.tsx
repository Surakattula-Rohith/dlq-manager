import { createContext, useContext } from 'react';

interface ThemeContextType {
  isDark: boolean;
  toggleDark: () => void;
}

export const ThemeContext = createContext<ThemeContextType>({
  isDark: false,
  toggleDark: () => {},
});

export const useTheme = () => useContext(ThemeContext);
