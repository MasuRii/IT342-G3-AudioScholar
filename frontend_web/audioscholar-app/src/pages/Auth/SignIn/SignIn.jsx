import { getAuth, GoogleAuthProvider, signInWithEmailAndPassword, signInWithPopup } from 'firebase/auth';
import React, { useState } from 'react';
import { FaGithub } from 'react-icons/fa';
import { FcGoogle } from 'react-icons/fc';
import { FiMic, FiCheckCircle, FiYoutube, FiUpload, FiCloud, FiBriefcase } from 'react-icons/fi';
import { Link, useNavigate } from 'react-router-dom';
import { firebaseApp } from '../../../config/firebaseConfig';
import { verifyFirebaseTokenWithBackend, verifyGoogleTokenWithBackend } from '../../../services/authService';
import { Footer, Header } from '../../Home/HomePage';

const SignIn = () => {
        const [email, setEmail] = useState('');
        const [password, setPassword] = useState('');
        const [loading, setLoading] = useState(false);
        const [error, setError] = useState(null);
        const navigate = useNavigate();
        const auth = getAuth(firebaseApp);

        // State for feature carousel
        const [currentFeatureIndex, setCurrentFeatureIndex] = useState(0);


        const handleBackendVerification = async (idToken) => {
                setError(null);
                setLoading(true);
                try {
                        console.log('Sending Firebase ID token to backend for verification...');
                        const backendResponse = await verifyFirebaseTokenWithBackend(idToken);

                        console.log('Backend verification successful (Firebase Token):', backendResponse);

                        localStorage.setItem('AuthToken', backendResponse.token);
                        localStorage.setItem('userId', backendResponse.userId);

                        navigate('/dashboard');

                } catch (err) {
                        console.error('Backend verification error (Firebase Token):', err);
                        setError(err.message || 'Failed to verify authentication with backend.');
                } finally {
                        setLoading(false);
                }
        };


        const handleSubmit = async (e) => {
                e.preventDefault();
                setError(null);

                if (!email || !password) {
                        setError('Please enter both email and password.');
                        return;
                }

                setLoading(true);
                try {
                        console.log('Attempting Firebase sign-in with email/password...');
                        const userCredential = await signInWithEmailAndPassword(auth, email, password);
                        const user = userCredential.user;
                        console.log('Firebase email/password sign-in successful for user:', user.uid);

                        const idToken = await user.getIdToken();
                        console.log('Obtained Firebase ID Token.');

                        await handleBackendVerification(idToken);

                } catch (err) {
                        console.error('Firebase email/password sign-in error:', err);
                        let errorMessage = 'Failed to sign in. Please check your credentials.';
                        if (err.code) {
                                switch (err.code) {
                                        case 'auth/user-not-found':
                                        case 'auth/wrong-password':
                                        case 'auth/invalid-email':
                                                errorMessage = 'Invalid email or password.';
                                                break;
                                        case 'auth/too-many-requests':
                                                errorMessage = 'Too many attempts. Please try again later.';
                                                break;
                                        default:
                                                errorMessage = err.message;
                                }
                        }
                        setError(errorMessage);
                        setLoading(false);
                }
        };


        const handleGoogleSignIn = async () => {
                setError(null);
                setLoading(true);
                const provider = new GoogleAuthProvider();
                provider.addScope('email');
                provider.addScope('profile');
                try {
                        console.log('Attempting Firebase sign-in with Google Popup...');
                        const result = await signInWithPopup(auth, provider);
                        const user = result.user;
                        console.log('Firebase Google sign-in successful for user:', user.uid);

                        const credential = GoogleAuthProvider.credentialFromResult(result);
                        const googleIdToken = credential?.idToken;

                        if (!googleIdToken) {
                                console.error("Could not extract Google ID Token from Firebase credential.");
                                throw new Error('Failed to get necessary Google credential.');
                        }
                        console.log('Obtained Google ID Token.');

                        console.log('Sending Google ID token to backend for verification...');
                        const backendResponse = await verifyGoogleTokenWithBackend(googleIdToken);
                        console.log('Backend verification successful (Google Token):', backendResponse);

                        localStorage.setItem('AuthToken', backendResponse.token);
                        localStorage.setItem('userId', backendResponse.userId);

                        navigate('/dashboard');

                } catch (err) {
                        console.error('Firebase Google sign-in or Backend verification error:', err);
                        let errorMessage = 'Failed to sign in with Google.';
                        if (err.code) {
                                switch (err.code) {
                                        case 'auth/popup-closed-by-user':
                                                errorMessage = 'Sign-in cancelled.';
                                                setLoading(false);
                                                return;
                                        case 'auth/account-exists-with-different-credential':
                                                errorMessage = 'An account already exists with this email using a different sign-in method.';
                                                break;
                                        default:
                                                errorMessage = err.message;
                                }
                        }
                        setError(errorMessage);
                        setLoading(false);
                }
        };

        const handleGithubSignIn = () => {
                const githubClientId = 'Iv23liMzUNGL8JuXu40i';

                const isProduction = window.location.hostname === 'it342-g3-audioscholar.onrender.com';
                const redirectBase = isProduction
                        ? 'https://it342-g3-audioscholar.onrender.com'
                        : window.location.origin;

                const redirectUri = `${redirectBase}/auth/github/callback`;

                console.log('Generated redirect_uri:', redirectUri);

                const scope = 'read:user user:email';

                const authUrl = `https://github.com/login/oauth/authorize?client_id=${githubClientId}&redirect_uri=${encodeURIComponent(redirectUri)}&scope=${encodeURIComponent(scope)}`;

                console.log('Redirecting to GitHub for authorization...');
                console.log('Constructed Auth URL:', authUrl);
                window.location.href = authUrl;
        };

        const signInFeatures = [
                {
                        icon: FiMic,
                        title: "Lecture Recording (Offline Capable)",
                        description: "Easily record lectures on your mobile, even offline. Upload pre-recorded audio via mobile or web. Focus on listening, not writing."
                },
                {
                        icon: FiCheckCircle,
                        title: "AI-Powered Summaries & Notes",
                        description: "Our AI processes your audio after the lecture to automatically generate structured summaries, key points, and topic lists."
                },
                {
                        icon: FiYoutube,
                        title: "Personalized Recommendations",
                        description: "Get relevant YouTube video recommendations based on your lecture content to deepen your understanding."
                },
                {
                        icon: FiUpload,
                        title: "PowerPoint Context (Optional)",
                        description: "Optionally upload lecture slides to provide context, enhancing the accuracy and relevance of AI summaries."
                },
                {
                        icon: FiCloud,
                        title: "Optional Cloud Sync",
                        description: "Securely sync recordings and notes to the cloud for backup and access across devices (manual or automatic)."
                },
                {
                        icon: FiBriefcase,
                        title: "Web Access & Management",
                        description: "Access your recordings, summaries, and recommendations, or upload audio files easily through our web interface."
                }
        ];

        const handleNextFeature = () => {
                setCurrentFeatureIndex((prevIndex) => (prevIndex + 1) % signInFeatures.length);
        };

        const handlePrevFeature = () => {
                setCurrentFeatureIndex((prevIndex) => (prevIndex - 1 + signInFeatures.length) % signInFeatures.length);
        };

        // const displayedFeature = signInFeatures.length > 0 ? signInFeatures[currentFeatureIndex] : null; // No longer needed


        return (
                <>
                        <Header />
                        <main className="flex-grow flex items-center justify-center py-12 bg-gray-50 overflow-hidden">
                                <title>AudioScholar - Sign In</title>
                                <div className="container mx-auto px-4 animate-fade-in-up">
                                        <div className="max-w-4xl mx-auto grid md:grid-cols-2 rounded-lg shadow-xl overflow-hidden">
                                                <div className="hidden md:block bg-[#2D8A8A] p-8 text-white flex flex-col h-full">
                                                        <div className="flex-shrink-0 mb-8">
                                                                <h2 className="text-3xl font-bold mb-3">Unlock Your Learning Potential</h2>
                                                                <p className="text-gray-200 text-sm">
                                                                        Sign in to access your personalized learning hub with AudioScholar.
                                                                </p>
                                                        </div>

                                                        {/* Carousel Section */}
                                                        {signInFeatures.length > 0 && (
                                                                <div className="flex flex-col items-center justify-center flex-grow">
                                                                        {/* Container for the two feature cards */}
                                                                        <div className="w-full max-w-sm space-y-4">
                                                                                {/* Feature Card 1 */}
                                                                                {( () => {
                                                                                        const FeatureIcon1 = signInFeatures[currentFeatureIndex].icon;
                                                                                        return (
                                                                                                <div key={`feature-card-1-${currentFeatureIndex}`} className="bg-black bg-opacity-25 p-5 rounded-xl shadow-lg w-full min-h-[150px] flex items-start transition-opacity duration-300 ease-in-out">
                                                                                                        <div className="w-11 h-11 bg-teal-500 rounded-lg flex items-center justify-center mr-4 flex-shrink-0">
                                                                                                                <FeatureIcon1 className="w-5 h-5 text-white" />
                                                                                                        </div>
                                                                                                        <div className="flex-grow">
                                                                                                                <h3 className="font-semibold text-md text-white mb-1.5">{signInFeatures[currentFeatureIndex].title}</h3>
                                                                                                                <p className="text-gray-300 text-xs leading-relaxed">{signInFeatures[currentFeatureIndex].description}</p>
                                                                                                        </div>
                                                                                                </div>
                                                                                        );
                                                                                })()}

                                                                                {/* Feature Card 2 (conditional) */}
                                                                                {signInFeatures.length > 1 && ( () => {
                                                                                        const secondIndex = (currentFeatureIndex + 1) % signInFeatures.length;
                                                                                        const FeatureIcon2 = signInFeatures[secondIndex].icon;
                                                                                        return (
                                                                                                <div key={`feature-card-2-${currentFeatureIndex}`} className="bg-black bg-opacity-25 p-5 rounded-xl shadow-lg w-full min-h-[150px] flex items-start transition-opacity duration-300 ease-in-out">
                                                                                                        <div className="w-11 h-11 bg-teal-500 rounded-lg flex items-center justify-center mr-4 flex-shrink-0">
                                                                                                                <FeatureIcon2 className="w-5 h-5 text-white" />
                                                                                                        </div>
                                                                                                        <div className="flex-grow">
                                                                                                                <h3 className="font-semibold text-md text-white mb-1.5">{signInFeatures[secondIndex].title}</h3>
                                                                                                                <p className="text-gray-300 text-xs leading-relaxed">{signInFeatures[secondIndex].description}</p>
                                                                                                        </div>
                                                                                                </div>
                                                                                        );
                                                                                })()}
                                                                        </div>

                                                                        {/* Navigation - below the cards */}
                                                                        {signInFeatures.length > 1 && ( // Only show nav if more than one feature to cycle through
                                                                                <div className="mt-8 w-full max-w-sm"> {/* Increased mt from mt-6 to mt-8 */}
                                                                                        <div className="flex items-center justify-center space-x-2.5 mb-4">
                                                                                                {signInFeatures.map((_, index) => (
                                                                                                        <button
                                                                                                                key={index}
                                                                                                                onClick={() => setCurrentFeatureIndex(index)}
                                                                                                                className={`w-2 h-2 rounded-full transition-all duration-300 ${currentFeatureIndex === index ? 'bg-white ring-2 ring-offset-2 ring-offset-[#2D8A8A] ring-white scale-110' : 'bg-gray-400 bg-opacity-40 hover:bg-opacity-60'}`}
                                                                                                                aria-label={`Go to feature ${index + 1}`}
                                                                                                        />
                                                                                                ))}
                                                                                        </div>
                                                                                </div>
                                                                        )}
                                                                </div>
                                                        )}
                                                        {/* Fallback or Spacer if no features, or to help with justify-between if used */}
                                                        {signInFeatures.length === 0 && <div className="flex-grow"></div>} {/* Adjusted condition from !displayedFeature */}
                                                </div>

                                                <div className="bg-white p-8 md:p-10">
                                                        <h1 className="text-3xl font-bold text-gray-800 mb-2">Sign In</h1>
                                                        <p className="text-gray-600 mb-6">Welcome back! Please enter your details or sign in with Google.</p>

                                                        <div className="flex flex-col sm:flex-row gap-4 mb-6">
                                                                <button
                                                                        onClick={handleGoogleSignIn}
                                                                        className="flex-1 flex items-center justify-center gap-2 py-2 px-4 border border-gray-300 rounded-lg hover:bg-gray-50 transition disabled:opacity-50"
                                                                        disabled={loading}
                                                                >
                                                                        <FcGoogle className="w-5 h-5" />
                                                                        <span className="text-sm font-medium text-gray-700">Sign in with Google</span>
                                                                </button>
                                                                <button
                                                                        onClick={handleGithubSignIn}
                                                                        className="flex-1 flex items-center justify-center gap-2 py-2 px-4 border border-gray-300 rounded-lg hover:bg-gray-50 transition disabled:opacity-50"
                                                                        disabled={loading}
                                                                >
                                                                        <FaGithub className="w-5 h-5" />
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
                                                                                value={email}
                                                                                onChange={(e) => setEmail(e.target.value)}
                                                                                required
                                                                                disabled={loading}
                                                                        />
                                                                </div>

                                                                <div>
                                                                        <label htmlFor="password" className="block text-sm font-medium text-gray-700 mb-1">Password</label>
                                                                        <input
                                                                                type="password"
                                                                                id="password"
                                                                                className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-[#2D8A8A] focus:border-[#2D8A8A]"
                                                                                placeholder="Enter your password"
                                                                                value={password}
                                                                                onChange={(e) => setPassword(e.target.value)}
                                                                                required
                                                                                disabled={loading}
                                                                        />
                                                                </div>

                                                                {error && (
                                                                        <p className="text-red-500 text-sm mt-2 text-center">{error}</p>
                                                                )}

                                                                <div className="flex items-center justify-between text-sm">
                                                                        <div className="flex items-center">
                                                                                <input id="remember-me" name="remember-me" type="checkbox" className="h-4 w-4 text-[#2D8A8A] focus:ring-[#236b6b] border-gray-300 rounded" />
                                                                                <label htmlFor="remember-me" className="ml-2 block text-gray-900">Remember me</label>
                                                                        </div>
                                                                        <Link to="/forgot-password" className="font-medium text-[#2D8A8A] hover:text-[#236b6b]">Forgot password?</Link>
                                                                </div>

                                                                <button
                                                                        type="submit"
                                                                        className={`w-full bg-[#2D8A8A] text-white py-3 px-4 rounded-lg font-medium transition ${loading ? 'opacity-50 cursor-not-allowed' : 'hover:bg-[#236b6b]'}`}
                                                                        disabled={loading}
                                                                >
                                                                        {loading ? 'Signing In...' : 'Log In with Email'}
                                                                </button>
                                                        </form>

                                                        <div className="mt-6 text-center">
                                                                <p className="text-sm text-gray-600">
                                                                        Don't have an account?{' '}
                                                                        {/* Ensure your Sign Up page also uses Firebase Auth */}
                                                                        <Link to="/signup" className="text-[#2D8A8A] hover:text-[#236b6b] font-medium">Sign up</Link>
                                                                </p>
                                                        </div>
                                                </div>
                                        </div>
                                </div>
                        </main>
                        <Footer />
                </>
        );
};

export default SignIn;