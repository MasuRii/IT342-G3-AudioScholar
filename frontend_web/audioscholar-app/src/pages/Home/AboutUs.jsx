import React from 'react';
import { Link } from 'react-router-dom';
// We won't import Header/Footer here as App.jsx will handle it

const AboutUs = () => {
  return (
    // No outer div or Header/Footer needed here if rendered within the App.jsx wildcard route
    <main className="flex-grow">
      {/* Hero Section */}
      <section className="bg-gradient-to-r from-[#1A365D] to-[#2D8A8A] py-20 text-white text-center">
        <div className="container mx-auto px-4">
          <h1 className="text-4xl md:text-5xl font-bold mb-4">About AudioScholar</h1>
          <p className="text-xl md:text-2xl text-indigo-100">
            Revolutionizing Learning Through Intelligent Audio Processing
          </p>
        </div>
      </section>

      {/* Our Mission */}
      <section className="py-16 bg-white">
        <div className="container mx-auto px-4 max-w-4xl">
          <h2 className="text-3xl font-bold text-center text-gray-800 mb-12">Our Mission</h2>
          <p className="text-lg text-gray-700 leading-relaxed text-center mb-6">
            At AudioScholar, our mission is to empower students, professionals, and lifelong learners
            by transforming spoken knowledge into accessible, structured, and actionable insights.
            We believe that valuable information shared in lectures, meetings, and discussions
            should be easily captured, understood, and revisited.
          </p>
          <p className="text-lg text-gray-700 leading-relaxed text-center">
            We leverage the power of cutting-edge AI to automate the tedious process of note-taking
            and summarizing, allowing you to focus on what truly matters: learning and comprehension.
          </p>
        </div>
      </section>

      {/* How It Works / Features */}
      <section className="py-16 bg-teal-50"> {/* Light teal background */}
        <div className="container mx-auto px-4">
          <h2 className="text-3xl font-bold text-center text-[#1A365D] mb-12">What We Offer</h2>
          <div className="grid md:grid-cols-3 gap-8 max-w-6xl mx-auto">
            {/* Feature 1: Smart Uploads */}
            <div className="bg-white p-6 rounded-lg shadow-md border border-gray-200 hover:shadow-lg transition-shadow">
              <div className="flex justify-center mb-4">
                <div className="bg-teal-100 p-3 rounded-full">
                  {/* Upload Icon */}
                  <svg xmlns="http://www.w3.org/2000/svg" className="h-8 w-8 text-teal-600" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                    <path strokeLinecap="round" strokeLinejoin="round" d="M7 16a4 4 0 01-.88-7.903A5 5 0 1115.9 6L16 6a5 5 0 011 9.9M15 13l-3-3m0 0l-3 3m3-3v12" />
                  </svg>
                </div>
              </div>
              <h3 className="text-xl font-semibold text-center text-teal-700 mb-3">Intelligent Uploads</h3>
              <p className="text-gray-600 text-center">
                Upload your lecture or meeting recordings effortlessly. Our platform accepts various audio formats
                and prepares them for AI processing.
              </p>
            </div>

            {/* Feature 2: AI Summaries */}
            <div className="bg-white p-6 rounded-lg shadow-md border border-gray-200 hover:shadow-lg transition-shadow">
              <div className="flex justify-center mb-4">
                 <div className="bg-indigo-100 p-3 rounded-full">
                   {/* AI/Idea Icon */}
                   <svg xmlns="http://www.w3.org/2000/svg" className="h-8 w-8 text-indigo-600" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                     <path strokeLinecap="round" strokeLinejoin="round" d="M9.663 17h4.673M12 3v1m6.364 1.636l-.707.707M21 12h-1M4 12H3m3.343-5.657l-.707-.707m2.828 9.9a5 5 0 117.072 0l-.548.547A3.374 3.374 0 0014 18.469V19a2 2 0 11-4 0v-.531c0-.895-.356-1.754-.988-2.386l-.548-.547z" />
                   </svg>
                 </div>
              </div>
              <h3 className="text-xl font-semibold text-center text-teal-700 mb-3">AI-Powered Insights</h3>
              <p className="text-gray-600 text-center">
                Receive concise, structured summaries, key points, vocabulary lists, and learning recommendations
                generated automatically by our advanced AI.
              </p>
            </div>

            {/* Feature 3: Organized Access */}
             <div className="bg-white p-6 rounded-lg shadow-md border border-gray-200 hover:shadow-lg transition-shadow">
               <div className="flex justify-center mb-4">
                  <div className="bg-blue-100 p-3 rounded-full">
                   {/* List/Organize Icon */}
                   <svg xmlns="http://www.w3.org/2000/svg" className="h-8 w-8 text-blue-600" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                     <path strokeLinecap="round" strokeLinejoin="round" d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2m-3 7h3m-3 4h3m-6-4h.01M9 16h.01" />
                   </svg>
                 </div>
               </div>
               <h3 className="text-xl font-semibold text-center text-teal-700 mb-3">Organized Access</h3>
               <p className="text-gray-600 text-center">
                 Your recordings and generated summaries are securely stored and organized,
                 accessible from your dashboard whenever you need them.
               </p>
             </div>
          </div>
        </div>
      </section>

      {/* Our Vision */}
      <section className="py-16 bg-white">
        <div className="container mx-auto px-4 max-w-4xl text-center">
          <h2 className="text-3xl font-bold text-gray-800 mb-6">Our Vision</h2>
          <p className="text-lg text-gray-700 leading-relaxed">
            We envision a future where learning from audio is as efficient and effective as reading.
            AudioScholar aims to be the essential tool for anyone looking to maximize their comprehension
            and retention of spoken information.
          </p>
        </div>
      </section>

      {/* Call to Action */}
      <section className="py-16 bg-[#1A365D]">
        <div className="container mx-auto px-4 text-center">
          <h2 className="text-3xl font-bold text-white mb-6">Ready to Enhance Your Learning?</h2>
          <p className="text-indigo-200 text-lg mb-8 max-w-2xl mx-auto">
            Join learners and professionals who are saving time and understanding more with AudioScholar.
            Sign up today and experience the future of audio intelligence.
          </p>
          <Link
            to="/signup"
            className="inline-block bg-teal-500 hover:bg-teal-600 text-white font-bold py-3 px-8 rounded-lg transition-colors shadow-md hover:shadow-lg transform hover:-translate-y-0.5"
          >
            Get Started Now
          </Link>
        </div>
      </section>
    </main>
  );
};

export default AboutUs; 