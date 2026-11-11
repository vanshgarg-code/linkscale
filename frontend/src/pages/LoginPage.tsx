import AuthForm from '../components/AuthForm';

export default function LoginPage({ setUser }: { setUser: (user: { email: string } | null) => void }) {
  return <AuthForm mode="login" setUser={setUser} />;
}
