import { createContext, useContext } from 'react';
import type { AuthSession, Role } from '../types';

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

// Each role can do everything the roles before it can (the server enforces the same rules)
const ROLE_LEVEL: Record<Role, number> = { VIEWER: 0, OPERATOR: 1, ADMIN: 2 };

export function usePermissions() {
  const { session } = useAuth();
  const level = session?.role ? ROLE_LEVEL[session.role] : -1;
  return {
    // Replay messages, acknowledge and snooze alerts
    canOperate: level >= ROLE_LEVEL.OPERATOR,
    // Change DLQ topics, alert rules, Slack channels and Kafka settings
    canAdminister: level >= ROLE_LEVEL.ADMIN,
  };
}
