import { createContext, useContext } from 'react';
import type { AuthSession } from '../types';

// React Query key of the "who is signed in" query
export const AUTH_SESSION_KEY = ['authSession'];

interface AuthContextType {
  session: AuthSession | null;
  logout: () => void;
}

export const AuthContext = createContext<AuthContextType>({
  session: null,
  logout: () => {},
});

export const useAuth = () => useContext(AuthContext);
