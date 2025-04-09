// components/SignIn.jsx
import { Link } from 'react-router-dom';
// Optional: Import icons if you have an icon library like react-icons
// import { FcGoogle } from 'react-icons/fc';
// import { FaGithub } from 'react-icons/fa';

const SignIn = () => {
  const handleSubmit = (e) => {
    e.preventDefault();
    // No validation, just proceed
    window.location.href = '/dashboard'; // or use navigate if you're using react-router
  };

  return (
    <main className="flex-grow flex items-center justify-center py-12 bg-gray-50">
      <div className="container mx-auto px-4">
        <div className="max-w-4xl mx-auto grid md:grid-cols-2 rounded-lg shadow-xl overflow-hidden">
          {/* Left Column (Informational Side - hidden on small screens) */}
          <div className="hidden md:block bg-[#2D8A8A] p-10 text-white flex flex-col justify-center">
            <h2 className="text-3xl font-bold mb-4">Welcome Back <br /> to AudioScholar</h2>
            <p className="text-gray-200 mb-8">
              Continue your journey of smarter learning with AI-powered lecture notes.
            </p>
            <div className="space-y-6">
              <div className="bg-black bg-opacity-20 p-4 rounded-lg">
                <h3 className="font-semibold mb-1">Seamless Sync</h3>
                <p className="text-sm text-gray-300">Access your lecture recordings and summaries across all your devices.</p>
              </div>
              <div className="bg-black bg-opacity-20 p-4 rounded-lg">
                <h3 className="font-semibold mb-1">Premium Features</h3>
                <p className="text-sm text-gray-300">Unlock background recording and unlimited summaries with your premium account.</p>
              </div>
            </div>
          </div>

          {/* Right Column (Sign In Form) */}
          <div className="bg-white p-8 md:p-10">
            <h1 className="text-3xl font-bold text-gray-800 mb-2">Sign In</h1>
            <p className="text-gray-600 mb-6">Welcome back! Please enter your details.</p>

            {/* Optional: Social Logins */}
            <div className="flex flex-col sm:flex-row gap-4 mb-6">
              <button className="flex-1 flex items-center justify-center gap-2 py-2 px-4 border border-gray-300 rounded-lg hover:bg-gray-50 transition">
                {/* <FcGoogle className="w-5 h-5" /> */}
                <span className="text-sm font-medium text-gray-700">Sign in with Google</span>
              </button>
              <button className="flex-1 flex items-center justify-center gap-2 py-2 px-4 border border-gray-300 rounded-lg hover:bg-gray-50 transition">
                {/* <FaGithub className="w-5 h-5" /> */}
                 <span className="text-sm font-medium text-gray-700">Sign in with Github</span>
              </button>
            </div>

            <form className="space-y-4" onSubmit={handleSubmit}>
              <div>
                <label htmlFor="email" className="block text-sm font-medium text-gray-700 mb-1">Email</label>
                <input
                  type="email"
                  id="email"
                  className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-[#2D8A8A] focus:border-[#2D8A8A]"
                  placeholder="Enter your email"
                />
              </div>

              <div>
                <label htmlFor="password" className="block text-sm font-medium text-gray-700 mb-1">Password</label>
                <div className="relative">
                  <input
                    type="password"
                    id="password"
                    className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-[#2D8A8A] focus:border-[#2D8A8A]"
                    placeholder="Enter your password"
                  />
                </div>
              </div>

              <div className="flex items-center justify-between text-sm">
                <div className="flex items-center">
                  <input id="remember-me" name="remember-me" type="checkbox" className="h-4 w-4 text-[#2D8A8A] focus:ring-[#236b6b] border-gray-300 rounded" />
                  <label htmlFor="remember-me" className="ml-2 block text-gray-900">Remember me</label>
                </div>
                <Link to="/forgot-password" className="font-medium text-[#2D8A8A] hover:text-[#236b6b]">Forgot password?</Link>
              </div>

              <button type="submit" className="w-full bg-[#2D8A8A] text-white py-3 px-4 rounded-lg font-medium hover:bg-[#236b6b] transition">
                Log In
              </button>
            </form>

            <div className="mt-6 text-center">
              <p className="text-sm text-gray-600">
                Don't have an account?{' '}
                <Link to="/signup" className="text-[#2D8A8A] hover:text-[#236b6b] font-medium">Sign up</Link>
              </p>
            </div>
          </div>
        </div>
      </div>
    </main>
  );
};

export default SignIn;