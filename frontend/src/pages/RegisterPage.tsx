import AuthForm from '../components/AuthForm';

export default function RegisterPage({ setUser }: { setUser: (user: { email: string } | null) => void }) {
  return <AuthForm mode="register" setUser={setUser} />;
}
